package org.eln2.mc.extensions

object CollectionExtensions {
    fun <T> ArrayDeque<T>.removeAll(action: ((T) -> Unit)): Int {
        var processed = 0

        while (!this.isEmpty()){
            action(this.removeFirst())
            processed++
        }

        return processed
    }
}
