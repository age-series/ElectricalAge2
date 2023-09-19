package org.eln2.mc.data

interface Histogram<K> {
    operator fun get(k: K): Int
    fun contains(k: K) = get(k) > 0
}

interface MutableHistogram<K> : Histogram<K> {
    operator fun set(k: K, n: Int)
    fun add(k: K, n: Int = 1)
    fun take(k: K, n: Int = 1)
    operator fun plusAssign(k: K) = add(k)
    operator fun minusAssign(k: K) = take(k)
    fun remove(k: K) = set(k, 0)
    fun clear()
}

class MutableMapHistogram<K>(val map: MutableMap<K, Int>) : MutableHistogram<K> {
    override fun get(k: K) = map[k] ?: 0
    override fun set(k: K, n: Int) {
        map[k] = n
    }

    override fun add(k: K, n: Int) {
        map[k] = get(k) + n
    }

    override fun take(k: K, n: Int) {
        val result = get(k) - n

        if (result < 0) {
            error("Tried to remove more elements than were available")
        }

        this[k] = result
    }

    override fun toString(): String {
        val sb = StringBuilder()

        map.forEach { (k, n) ->
            sb.appendLine("$k: $n")
        }

        return sb.toString()
    }

    override fun clear() = map.clear()

    fun removeMapping(k: K) = map.remove(k)
}

fun <K> HashMapHistogram() = MutableMapHistogram<K>(HashMap())
fun <K> LinkedHashMapHistogram() = MutableMapHistogram<K>(LinkedHashMap())
