package org.eln2.mc.common.events

import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.mc.Eln2.LOGGER
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue

fun interface IEventQueueAccess {
    fun enqueueEvent(event: IEvent): Boolean
}

fun interface IWorkItem {
    fun execute()
}

/**
 * The event scheduler can be used to schedule events from any thread, that are to be sent on a game tick.
 * */
@Mod.EventBusSubscriber
object EventScheduler {
    private class EventQueue(listener: IEventListener) {
        val manager = EventManager()
        val queue = ConcurrentLinkedQueue<IEvent>()
        var valid = true
    }

    private var timeStamp: Int = 0

    private data class SynchronousWork(val timeStamp: Int, val item: IWorkItem)

    private val eventQueues = ConcurrentHashMap<IEventListener, EventQueue>()

    private val workComparator = Comparator<SynchronousWork> { (c1, _), (c2, _) ->
        c1.compareTo(c2)
    }

    private val scheduledWorkPre = PriorityBlockingQueue(1024, workComparator)
    private val scheduledWorkPost = PriorityBlockingQueue(1024, workComparator)

    private fun getEventQueue(listener: IEventListener): EventQueue {
        return eventQueues[listener]
            ?: error("Could not find event queue for $listener")
    }

    /**
     * Gets the Event Manager of the queue of the specified listener.
     * */
    fun getManager(listener: IEventListener): EventManager {
        return getEventQueue(listener).manager
    }

    fun scheduleWorkPre(countdown: Int, item: IWorkItem){
        scheduledWorkPre.add(SynchronousWork(timeStamp + countdown, item))
    }

    fun scheduleWorkPost(countdown: Int, item: IWorkItem){
        scheduledWorkPost.add(SynchronousWork(timeStamp + countdown, item))
    }

    /**
     * Creates an event queue for the specified listener.
     * Only one queue can exist per listener. An error will be raised if this listener already registered a queue.
     * This queue can be subsequently accessed, and events can be enqueued for the next tick.
     * */
    fun register(listener: IEventListener) {
        if (eventQueues.put(listener, EventQueue(listener)) != null) {
            error("Duplicate add $listener")
        }
    }

    /**
     * Gets a queue access to the specified listener.
     * This may be used concurrently.
     * Events enqueued using this access will be sent to the listener on the next tick.
     * */
    fun getEventAccess(listener: IEventListener): IEventQueueAccess {
        val eventQueue = getEventQueue(listener)

        return IEventQueueAccess {
            if (!eventQueue.valid) {
                // To be fair, this value could change just after checking.
                // For fun, we log.

                LOGGER.warn("Lingering event access")

                return@IEventQueueAccess false
            }

            eventQueue.queue.add(it)

            return@IEventQueueAccess true
        }
    }

    /**
     * Gets the event access for the specified listener, and enqueues an event for the next tick.
     * */
    fun enqueueEvent(listener: IEventListener, event: IEvent) {
        getEventAccess(listener).enqueueEvent(event)
    }

    /**
     * Destroys the event queue of the specified listener. Results in an exception if the listener was not registered beforehand.
     * */
    fun remove(listener: IEventListener) {
        val removed = eventQueues.remove(listener) ?: error("Could not find queue for $listener")
        removed.valid = false
    }

    /**
     * On every tick, we traverse all event queues, and send the events to the Event Manager.
     * */
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            doSynchronousWork(scheduledWorkPre)

            eventQueues.values.forEach {
                val queue = it.queue
                val manager = it.manager

                while (true) {
                    manager.send(queue.poll() ?: break)
                }
            }
        }
        else if(event.phase == TickEvent.Phase.END){
            doSynchronousWork(scheduledWorkPost)
        }

        timeStamp++
    }

    private fun doSynchronousWork(queue: PriorityBlockingQueue<SynchronousWork>){
        while (true){
            val first = queue.peek()
                ?: break

            if(first.timeStamp > timeStamp){
                break
            }

            queue.remove()

            first.item.execute()
        }
    }
}
