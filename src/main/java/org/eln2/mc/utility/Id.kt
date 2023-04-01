package org.eln2.mc.utility

import java.util.concurrent.atomic.AtomicInteger

@JvmInline
value class IntId(val id: Int) {
    companion object {
        private val current = AtomicInteger()

        fun create(): IntId {
            return IntId(current.getAndIncrement())
        }
    }
}
