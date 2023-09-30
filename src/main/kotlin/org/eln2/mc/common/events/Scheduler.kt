package org.eln2.mc.common.events

import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.Phase
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.mc.CrossThreadAccess
import org.eln2.mc.ServerOnly
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

fun interface EventQueue {
    /**
     * Enqueues the specified event to be sent.
     * @return True if the queue was valid at the time of sending. This **does not guarantee** the event will be received, because the receiver could be destroyed before the events are dispatched.
     * */
    fun enqueue(event: Event): Boolean
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

    private var timeStamp = 0L
    private val eventQueues = HashMap<EventListener, EventQueueImplementation>()
    private val lock = ReentrantReadWriteLock()

    private val workQueues = mapOf(
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
            for (it in eventQueues.values) {
                val queue = it.queue
                val manager = it.manager

                while (true) {
                    manager.send(queue.poll() ?: break)
                }
            }
        }
    }

    private class EventQueueImplementation : EventQueue {
        val manager = EventManager()
        val queue = ConcurrentLinkedQueue<Event>()
        var valid = true

        override fun enqueue(event: Event): Boolean {
            if(!valid) {
                return false
            }

            queue.add(event)

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
