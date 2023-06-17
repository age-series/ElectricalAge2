package org.eln2.mc.data

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ReadWrite<T>(private val instance: T) {
    private val lock = ReentrantReadWriteLock()

    @OptIn(ExperimentalContracts::class)
    fun write(action: (T) -> Unit) {
        contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
        lock.write {
            action(instance)
        }
    }

    @OptIn(ExperimentalContracts::class)
    fun read(action: (T) -> Unit) {
        contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
        lock.read {
            action(instance)
        }
    }

    @OptIn(ExperimentalContracts::class)
    fun writeOptionalIf(readBlock: (T) -> Boolean, writeBlock: (T) -> Unit) {
        contract { callsInPlace(readBlock, InvocationKind.EXACTLY_ONCE) }

        var isWrite: Boolean

        read {
            isWrite = readBlock(it)
        }

        if(isWrite) {
            write(writeBlock)
        }
    }
}
