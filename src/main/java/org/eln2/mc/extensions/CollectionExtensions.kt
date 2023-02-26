package org.eln2.mc.extensions

object CollectionExtensions {
    fun <T> ArrayDeque<T>.removeAll(action: ((T) -> Unit)): Int {
        var processed = 0

        while (true){
            if(this.isEmpty()){
                return processed
            }

            action(this.removeFirst())
            processed++
        }
    }
}
