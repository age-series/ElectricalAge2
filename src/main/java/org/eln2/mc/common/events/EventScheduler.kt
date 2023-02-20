package org.eln2.mc.common.events

import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

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

    private val eventQueues = ConcurrentHashMap<IEventListener, EventQueue>()

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
    fun getEventAccess(listener: IEventListener): (IEvent) -> Unit {
        val eventQueue = getEventQueue(listener)

        return {
            if (!eventQueue.valid) {
                error("Tried to send event after queue became invalid")
            }

            eventQueue.queue.add(it)
        }
    }

    /**
     * Gets the event access for the specified listener, and enqueues an event for the next tick.
     * */
    fun enqueueEvent(listener: IEventListener, event: IEvent) {
        getEventAccess(listener).invoke(event)
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
            eventQueues.values.forEach {
                val queue = it.queue
                val manager = it.manager

                while (true) {
                    manager.send(queue.poll() ?: break)
                }
            }
        }
    }
}
