package org.eln2.mc.extensions

import com.mojang.math.Quaternion

operator fun Quaternion.times(other: Quaternion): Quaternion {
    val copy = this.copy()

    copy.mul(other)

    return copy
}

operator fun Quaternion.timesAssign(other: Quaternion) {
    this.mul(other)
}
