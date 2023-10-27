package org.eln2.mc.common.events

import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.Phase
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.mc.CrossThreadAccess
import org.eln2.mc.ServerOnly
import org.eln2.mc.atomicRemoveIf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

fun interface EventDiscardedCallback<T : Event> {
    fun discard(event: T)
}

interface EventQueue {
    /**
     * Enqueues the specified event to be sent.
     * @return True, if the queue was valid at the time of sending. This **does not guarantee** that the event will be received, because the receiver could be destroyed before the events are dispatched.
     * */
    fun<T : Event> enqueue(event: T) : Boolean

    /**
     * Enqueues the specified event to be sent.
     * @param discardCallback A callback that gets invoked if the event receiver gets destroyed after the event is enqueued, but before it gets dispatched.
     * @return True, if the queue was valid at the time of sending. This **does not guarantee** that the event will be received, because the receiver could be destroyed before the events are dispatched.
     * */
    fun<T : Event> enqueue(event: T, discardCallback: EventDiscardedCallback<T>) : Boolean

    /**
     * Enqueues the specified event to be sent.
     * Only the latest value (at the time of inspection) will be sent, and previous values are discarded.
     * @return True, if the queue was valid at the time of sending. This **does not guarantee** that the event will be received, because the receiver could be destroyed before the events are dispatched.
     * */
    fun place(event: Event) : Boolean
}

interface ScheduledWork {
    fun cancel()
}

/**
 * The Event Scheduler is used to execute work and dispatch events to the server thread.
 * It is used in scenarios where e.g. simulation threads need to signal a game object, that will subsequently
 * perform game logic.
 * */
@Mod.EventBusSubscriber
object Scheduler {
    private var timeStamp = 0L
    private val eventQueues = HashMap<EventListener, EventQueueImplementation>()
    private val lock = ReentrantReadWriteLock()

    private val workQueues : Map<Phase, WorkQueue> = mapOf(
        Phase.START to WorkQueue(),
        Phase.END to WorkQueue()
    )

    private fun getEventQueue(listener: EventListener): EventQueueImplementation {
        val result: EventQueueImplementation

        lock.read {
            result = eventQueues[listener] ?: error("Could not find event queue for $listener")
        }

        return result
    }

    @CrossThreadAccess
    fun getManager(listener: EventListener) = getEventQueue(listener).manager

    fun scheduleWork(countdown: Int, item: () -> Unit, phase: Phase) =
        workQueues[phase]!!.scheduleOnce(timeStamp + countdown.toLong(), item)

    fun scheduleWorkPeriodic(interval: Int, item: () -> Boolean, phase: Phase) =
        workQueues[phase]!!.schedulePeriodic(timeStamp + interval, interval, item)

    /**
     * Creates an event queue for the specified listener.
     * Only one queue can exist per listener.
     * This queue can be subsequently accessed, and events can be enqueued for the next tick.
     * */
    fun register(listener: EventListener): EventManager {
        val result = EventQueueImplementation()

        lock.write {
            if (eventQueues.put(listener, result) != null) {
                error("Duplicate add $listener")
            }
        }

        return result.manager
    }

    /**
     * Gets a queue access to the specified listener.
     * This may be used concurrently.
     * Events enqueued using this access will be sent to the listener on the next tick.
     * */
    @CrossThreadAccess
    fun getEventAccess(listener: EventListener): EventQueue = getEventQueue(listener)

    /**
     * Destroys the event queue of the specified listener.
     * */
    fun remove(listener: EventListener) {
        lock.write {
            val removed = eventQueues.remove(listener) ?: error("Could not find queue for $listener")
            removed.valid = false

            while (true) {
                val pair = removed.eventStream.poll() ?: break
                pair.second?.run()
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        workQueues[event.phase]!!.dispatchWork(timeStamp)

        if (event.phase == Phase.START) {
            dispatchEvents()
        }

        if (event.phase == Phase.END) {
            timeStamp++
        }
    }

    private fun dispatchEvents() {
        // Todo: hold a list of dirty queues so we don't traverse the full set

        lock.read {
            for (eventQueueImplementation in eventQueues.values) {
                val queue = eventQueueImplementation.eventStream
                val manager = eventQueueImplementation.manager

                while (true) {
                    val pair = queue.poll() ?: break
                    manager.send(pair.first)
                }

                eventQueueImplementation.eventsUnique.atomicRemoveIf { (_, v) ->
                    manager.send(v)
                    true
                }
            }
        }
    }

    private class WorkQueue {
        private val idAtomic = AtomicLong()

        data class WorkItem(val timeStamp: Long, val item: () -> Unit, val id: Long) : ScheduledWork {
            var cancelled = false

            override fun cancel() {
                cancelled = true
            }
        }

        val queue = PriorityBlockingQueue<WorkItem>(1024) { a, b ->
            a.timeStamp.compareTo(b.timeStamp)
        }

        fun scheduleOnce(timestamp: Long, item: () -> Unit) = queue.add(
            WorkItem(
                timestamp,
                item,
                idAtomic.getAndIncrement()
            )
        )

        fun schedulePeriodic(timeStamp: Long, interval: Int, item: () -> Boolean): ScheduledWork {
            var cancelled = false

            val cancelWrapper = object : ScheduledWork {
                override fun cancel() {
                    cancelled = true
                }
            }

            var currentTimeStamp = timeStamp

            fun work() {
                if (!cancelled && item()) {
                    currentTimeStamp += interval
                    scheduleOnce(currentTimeStamp, ::work)
                }
            }

            scheduleOnce(timeStamp, ::work)

            return cancelWrapper
        }

        fun dispatchWork(actualTimeStamp: Long): Int {
            var dispatchCount = 0

            while (true) {
                val front = queue.peek()
                    ?: break

                if (front.timeStamp > actualTimeStamp) {
                    // Because our queue is sorted, it means there are no items
                    // that we should execute this tick.
                    break
                }

                // It is also fine if we get another item than the one we examined.
                require(queue.remove(front))

                if (!front.cancelled) {
                    front.item()
                }

                ++dispatchCount
            }

            return dispatchCount
        }
    }

    private class EventQueueImplementation : EventQueue {
        val manager = EventManager()
        val eventStream = ConcurrentLinkedQueue<Pair<Event, Runnable?>>()
        val eventsUnique = ConcurrentHashMap<Class<*>, Event>()
        var valid = true

        override fun<T : Event> enqueue(event: T): Boolean {
            if(!valid) {
                return false
            }

            eventStream.add(Pair(event, null))

            return true
        }
        override fun<T : Event> enqueue(event: T, discardCallback : EventDiscardedCallback<T>): Boolean {
            if(!valid) {
                return false
            }

            eventStream.add(Pair(event, Runnable {
                discardCallback.discard(event)
            }))

            return true
        }

        override fun place(event: Event): Boolean {
            if(!valid) {
                return false
            }

            eventsUnique[event.javaClass] = event

            return true
        }
    }
}

@ServerOnly
fun schedulePre(countdown: Int, item: () -> Unit) = Scheduler.scheduleWork(countdown, item, Phase.START)

@ServerOnly
fun schedulePost(countdown: Int, item: () -> Unit) = Scheduler.scheduleWork(countdown, item, Phase.END)

@ServerOnly
fun runPre(block: () -> Unit) = Scheduler.scheduleWork(0, block, Phase.START)

@ServerOnly
fun runPost(block: () -> Unit) = Scheduler.scheduleWork(0, block, Phase.END)

@ServerOnly
fun periodicPre(interval: Int, item: () -> Boolean) = Scheduler.scheduleWorkPeriodic(interval, item, Phase.START)

@ServerOnly
fun periodicPost(interval: Int, item: () -> Boolean) = Scheduler.scheduleWorkPeriodic(interval, item, Phase.END)
