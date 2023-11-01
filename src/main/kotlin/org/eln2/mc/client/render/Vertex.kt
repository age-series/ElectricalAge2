@file:Suppress("NOTHING_TO_INLINE")

package org.eln2.mc.client.render

import org.eln2.mc.mathematics.Vector3d

class PositionNormalVertexArray3d(val store: DoubleArray, val size: Int) {
    constructor(size: Int) : this(DoubleArray(size * 6), size)

    init {
        require(store.size == size * 6)
    }

    inline fun getPositionX(index: Int) = store[index * 6 + 0]
    inline fun getPositionY(index: Int) = store[index * 6 + 1]
    inline fun getPositionZ(index: Int) = store[index * 6 + 2]
    inline fun getNormalX(index: Int) = store[index * 6 + 3]
    inline fun getNormalY(index: Int) = store[index * 6 + 4]
    inline fun getNormalZ(index: Int) = store[index * 6 + 5]
    inline fun setPositionX(index: Int, value: Double) { store[index * 6 + 0] = value }
    inline fun setPositionY(index: Int, value: Double) { store[index * 6 + 1] = value }
    inline fun setPositionZ(index: Int, value: Double) { store[index * 6 + 2] = value }
    inline fun setNormalX(index: Int, value: Double) { store[index * 6 + 3] = value }
    inline fun setNormalY(index: Int, value: Double) { store[index * 6 + 4] = value }
    inline fun setNormalZ(index: Int, value: Double) { store[index * 6 + 5] = value }

    inline fun getPosition(index: Int) = Vector3d(getPositionX(index), getPositionY(index), getPositionZ(index))
    inline fun getNormal(index: Int) = Vector3d(getNormalX(index), getNormalY(index), getNormalZ(index))

    inline fun setPosition(index: Int, value: Vector3d) {
        setPositionX(index, value.x)
        setPositionY(index, value.y)
        setPositionZ(index, value.z)
    }

    inline fun setNormal(index: Int, value: Vector3d) {
        setNormalX(index, value.x)
        setNormalY(index, value.y)
        setNormalZ(index, value.z)
    }

    inline fun forEachVertex(crossinline consumer: (px: Double, py: Double, pz: Double, nx: Double, ny: Double, nz: Double) -> Unit) {
        var i = 0
        while (i < size) {
            consumer(
                getPositionX(i), getPositionY(i), getPositionZ(i),
                getNormalX(i), getNormalY(i), getNormalZ(i)
            )
            i++
        }
    }
}
