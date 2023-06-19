package org.eln2.mc.data

import org.ageseries.libage.data.MutableSetMapMultiMap

data class EdgeInfo<V, E>(val b: V, val e: E)

interface Graph<V, E> {
    val vertices: Set<V>
    operator fun get(v: V): Set<EdgeInfo<V, E>>
}

interface MutableGraph<V, E> : Graph<V, E> {
    fun associate(a: V, b: V, e: E)
}

class MutableSetMultiMapGraph<V, E> : MutableGraph<V, E> {
    private val map = MutableSetMapMultiMap<V, EdgeInfo<V, E>>()
    private val vertexSet = HashSet<V>()

    override val vertices: Set<V> get() = vertexSet

    override fun associate(a: V, b: V, e: E) {
        vertexSet.add(a)
        vertexSet.add(b)
        map[a].add(EdgeInfo(b, e))
        map[b].add(EdgeInfo(a, e))
    }

    override fun get(v: V) = map[v]
}
