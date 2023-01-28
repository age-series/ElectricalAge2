package org.eln2.mc.extensions

import com.mojang.math.Vector3f
import com.mojang.math.Vector4f

object Vector4fExtensions {
    fun Vector4f.toVector3f() : Vector3f{
        return Vector3f(this.x(), this.y(), this.z())
    }
}
