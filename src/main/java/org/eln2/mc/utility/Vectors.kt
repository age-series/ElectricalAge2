package org.eln2.mc.utility

import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

object Vectors {
    fun componentMin(a: Vector3f, b: Vector3f): Vector3f {
        return Vector3f(
            min(a.x(), b.x()),
            min(a.y(), b.y()),
            min(a.z(), b.z())
        )
    }

    fun componentMax(a: Vector3f, b: Vector3f): Vector3f {
        return Vector3f(
            max(a.x(), b.x()),
            max(a.y(), b.y()),
            max(a.z(), b.z())
        )
    }
}
