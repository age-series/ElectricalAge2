package org.eln2.mc.extensions

import com.mojang.math.Matrix4f

object MatrixExtensions {
    operator fun Matrix4f.times(other: Matrix4f): Matrix4f {
        val copy = this.copy()

        copy.multiply(other)

        return copy
    }

    operator fun Matrix4f.timesAssign(other: Matrix4f) {
        this.multiply(other)
    }
}
