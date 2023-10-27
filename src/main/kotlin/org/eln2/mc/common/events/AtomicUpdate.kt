package org.eln2.mc.common.events

import java.util.concurrent.atomic.AtomicReference

class AtomicUpdate<T> {
    private val reference = AtomicReference<T>(null)

    val isPending get() = reference.get() != null

    fun setLatest(value: T?): T? = reference.getAndSet(value)

    fun setWithCompare(value: T?) : T? = reference.compareAndExchange(null, value)

    fun consume(consumer: ((T) -> Unit)): Boolean {
        val value = reference.getAndSet(null)
            ?: return false

        consumer(value)

        return true
    }
}
