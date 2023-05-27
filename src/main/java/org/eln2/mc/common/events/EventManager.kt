package org.eln2.mc.common.events

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * The event manager manages a list of event handlers.
 * An event can be sent, and it will be sent to all handlers.
 * It is supposedly thread-safe.
 * */
class EventManager {
    private val handlers = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<EventHandler<Event>>>()

    /**
     * Registers an event handler for events of type TEvent.
     * */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified TEvent : Event> registerHandler(handler: EventHandler<TEvent>) {
        registerHandler(TEvent::class, handler as EventHandler<Event>)
    }

    fun registerHandler(eventClass: KClass<*>, handler: EventHandler<Event>) {
        handlers.computeIfAbsent(eventClass) { CopyOnWriteArrayList() }.add(handler)
    }

    /**
     * Removes an event handler for events of type TEvent.
     * */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified TEvent : Event> unregisterHandler(handler: EventHandler<TEvent>) {
        unregisterHandler(TEvent::class, handler as EventHandler<Event>)
    }

    fun unregisterHandler(eventClass: KClass<*>, handler: EventHandler<Event>) {
        val handlers = handlers[eventClass]
            ?: error("Could not find handlers for $eventClass")

        if (!handlers.remove(handler)) {
            error("Could not remove handler $handler")
        }
    }

    /**
     * Sends an event to all subscribed listeners.
     * */
    fun send(event: Event) {
        val listeners = this.handlers[event::class]
            ?: return

        listeners.forEach {
            it.handle(event)
        }
    }
}

/**
 * Marker interface implemented by all events.
 * @see EventManager
 * */
interface Event

/**
 * A handler for events of the specified type.
 * */
fun interface EventHandler<T : Event> {
    fun handle(event: T)
}

/**
 * Event Listeners are implemented by game objects.
 * @see EventScheduler.register
 * */
interface EventListener
