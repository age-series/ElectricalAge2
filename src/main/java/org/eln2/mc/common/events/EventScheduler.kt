package org.eln2.mc.common.events

import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.CrossThreadAccess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue

fun interface EventQueue {
    fun enqueue(event: IEvent): Boolean
}

fun interface WorkItem {
    fun execute()
}

/**
 * The Event Scheduler is used to execute work and dispatch events to the server thread.
 * It is used in scenarios where e.g. simulation threads need to signal a game object, that will subsequently
 * perform game logic.
 * */
@Mod.EventBusSubscriber
object EventScheduler {
    private class EventQueue {
        val manager = EventManager()
        val queue = ConcurrentLinkedQueue<IEvent>()
        var valid = true
    }

    private var timeStamp: Int = 0

    private data class SynchronousWork(val timeStamp: Int, val item: WorkItem)

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
     * Gets the Event Manager of the listener.
     * */
    @CrossThreadAccess
    fun getManager(listener: IEventListener): EventManager {
        return getEventQueue(listener).manager
    }

    @CrossThreadAccess
    fun scheduleWorkPre(countdown: Int, item: WorkItem){
        scheduledWorkPre.add(SynchronousWork(timeStamp + countdown, item))
    }

    @CrossThreadAccess
    fun scheduleWorkPost(countdown: Int, item: WorkItem){
        scheduledWorkPost.add(SynchronousWork(timeStamp + countdown, item))
    }

    /**
     * Creates an event queue for the specified listener.
     * Only one queue can exist per listener.
     * This queue can be subsequently accessed, and events can be enqueued for the next tick.
     * */
    fun register(listener: IEventListener) {
        if (eventQueues.put(listener, EventQueue()) != null) {
            error("Duplicate add $listener")
        }
    }

    /**
     * Gets a queue access to the specified listener.
     * This may be used concurrently.
     * Events enqueued using this access will be sent to the listener on the next tick.
     * */
    @CrossThreadAccess
    fun getEventAccess(listener: IEventListener): org.eln2.mc.common.events.EventQueue {
        val eventQueue = getEventQueue(listener)

        return EventQueue {
            if (!eventQueue.valid) {
                // To be fair, this value could change just after checking.
                // For fun, we log.

                LOGGER.warn("Lingering event access")

                return@EventQueue false
            }

            eventQueue.queue.add(it)

            return@EventQueue true
        }
    }

    @CrossThreadAccess
    fun enqueueEvent(listener: IEventListener, event: IEvent) {
        getEventAccess(listener).enqueue(event)
    }

    /**
     * Destroys the event queue of the specified listener.
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

            dispatchEvents()
        }
        else if(event.phase == TickEvent.Phase.END){
            doSynchronousWork(scheduledWorkPost)

            timeStamp++
        }
    }

    private fun dispatchEvents() {
        // Todo: hold a list of dirty queues so we don't traverse the full set

        eventQueues.values.forEach {
            val queue = it.queue
            val manager = it.manager

            while (true) {
                manager.send(queue.poll() ?: break)
            }
        }
    }

    private fun doSynchronousWork(queue: PriorityBlockingQueue<SynchronousWork>){
        while (true){
            val first = queue.peek()
                ?: break

            if(first.timeStamp > timeStamp){
                // Because our queue is sorted, it means there are no items
                // that we should execute this tick.

                break
            }

            assert(queue.remove(first))

            first.item.execute()
        }
    }
}
