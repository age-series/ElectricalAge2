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

    fun getManager(listener: IEventListener): EventManager {
        return getEventQueue(listener).manager
    }

    fun register(listener: IEventListener) {
        if (eventQueues.put(listener, EventQueue(listener)) != null) {
            error("Duplicate add $listener")
        }
    }

    fun getEventAccess(listener: IEventListener): (IEvent) -> Unit {
        val eventQueue = getEventQueue(listener)

        return {
            if (!eventQueue.valid) {
                error("Tried to send event after queue became invalid")
            }

            eventQueue.queue.add(it)
        }
    }

    fun enqueueEvent(listener: IEventListener, event: IEvent) {
        getEventAccess(listener).invoke(event)
    }

    fun remove(listener: IEventListener) {
        val removed = eventQueues.remove(listener) ?: error("Could not find queue for $listener")
        removed.valid = false
    }

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
