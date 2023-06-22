package org.eln2.mc.common.events

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

interface EventHandlerManager {
    fun registerHandler(eventClass: KClass<*>, handler: EventHandler<Event>)
    fun unregisterHandler(eventClass: KClass<*>, handler: EventHandler<Event>)
}

interface EventDispatcher {
    fun send(event: Event): Int
}

/**
 * The event manager manages a list of event handlers.
 * An event can be sent, and it will be sent to all handlers.
 * It is supposedly thread-safe.
 * */
class EventManager(private val allowList: Set<KClass<*>>? = null) : EventHandlerManager, EventDispatcher {
    private val handlers = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<EventHandler<Event>>>()

    private fun validateEvent(k: KClass<*>) {
        if (allowList != null) {
            require(allowList.contains(k)) {
                "The event manager prohibits $k"
            }
        }

    }

    override fun registerHandler(eventClass: KClass<*>, handler: EventHandler<Event>) {
        validateEvent(eventClass)
        handlers.computeIfAbsent(eventClass) { CopyOnWriteArrayList() }.add(handler)
    }

    override fun unregisterHandler(eventClass: KClass<*>, handler: EventHandler<Event>) {
        val handlers = handlers[eventClass]
            ?: error("Could not find handlers for $eventClass")

        if (!handlers.remove(handler)) {
            error("Could not remove handler $handler")
        }
    }

    /**
     * Sends an event to all subscribed listeners.
     * */
    override fun send(event: Event): Int {
        validateEvent(event.javaClass.kotlin)

        val listeners = this.handlers[event::class]
            ?: return 0

        listeners.forEach {
            it.handle(event)
        }

        return listeners.size
    }
}

/**
 * Registers an event handler for events of type TEvent.
 * */
@Suppress("UNCHECKED_CAST")
inline fun <reified TEvent : Event> EventHandlerManager.registerHandler(handler: EventHandler<TEvent>) {
    registerHandler(TEvent::class, handler as EventHandler<Event>)
}

/**
 * Removes an event handler for events of type TEvent.
 * */
@Suppress("UNCHECKED_CAST")
inline fun <reified TEvent : Event> EventHandlerManager.unregisterHandler(handler: EventHandler<TEvent>) {
    unregisterHandler(TEvent::class, handler as EventHandler<Event>)
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
 * @see Scheduler.register
 * */
interface EventListener
