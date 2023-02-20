package org.eln2.mc.common.events

/**
 * A handler for events of the specified type.
 * */
fun interface IEventHandler<T : IEvent> {
    fun handle(event: T)
}
