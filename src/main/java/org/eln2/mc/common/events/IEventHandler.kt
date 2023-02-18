package org.eln2.mc.common.events

fun interface IEventHandler<T> {
    fun handle(event: T)
}
