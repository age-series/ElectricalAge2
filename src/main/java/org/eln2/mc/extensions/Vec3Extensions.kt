package org.eln2.mc.extensions

import com.mojang.math.Vector3f
import com.mojang.math.Vector4f
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3

operator fun Vec3.plus(b: Vec3): Vec3 {
    return Vec3(this.x + b.x, this.y + b.y, this.z + b.z)
}

operator fun Vec3.plus(delta: Double): Vec3 {
    return Vec3(this.x + delta, this.y + delta, this.z + delta)
}

operator fun Vec3.minus(b: Vec3): Vec3 {
    return Vec3(this.x - b.x, this.y - b.y, this.z - b.z)
}

operator fun Vec3.minus(delta: Double): Vec3 {
    return Vec3(this.x - delta, this.y - delta, this.z - delta)
}

operator fun Vec3.times(b: Vec3): Vec3 {
    return Vec3(this.x * b.x, this.y * b.y, this.z * b.z)
}

operator fun Vec3.times(scalar: Double): Vec3 {
    return Vec3(this.x * scalar, this.y * scalar, this.z * scalar)
}

operator fun Vec3.div(b: Vec3): Vec3 {
    return Vec3(this.x / b.x, this.y / b.y, this.z / b.z)
}

operator fun Vec3.div(scalar: Double): Vec3 {
    return Vec3(this.x / scalar, this.y / scalar, this.z / scalar)
}

operator fun Vec3.unaryMinus(): Vec3 {
    return Vec3(-this.x, -this.y, -this.z)
}

operator fun Vec3.unaryPlus(): Vec3 {
    // For completeness

    return Vec3(this.x, this.y, this.z)
}

fun Vec3i.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun Vector3f.toVec3(): Vec3 {
    return Vec3(this.x().toDouble(), this.y().toDouble(), this.z().toDouble())
}

fun Vec3i.toVector3f(): Vector3f {
    return Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
}

fun Vec3.toVector3f(): Vector3f {
    return Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
}

fun Vec3.toVector4f(w: Float): Vector4f {
    return Vector4f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat(), w)
}
