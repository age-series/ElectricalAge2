package org.eln2.mc.utility

import net.minecraft.network.FriendlyByteBuf

class McColor(val r : UByte, val g : UByte, val b : UByte, val a : UByte) {
    constructor(r : Int, g : Int, b : Int, a : Int) : this(
        if (r < 256 && r > -1) r.toUByte() else error("R is not within bounds."),
        if (g < 256 && g > -1) g.toUByte() else error("G is not within bounds."),
        if (b < 256 && b > -1) b.toUByte() else error("B is not within bounds."),
        if (a < 256 && a > -1) a.toUByte() else error("A is not within bounds."))

    constructor(r : Int, g : Int, b : Int) : this(r, g, b, 255)

    constructor(r : UByte, g : UByte, b : UByte) : this(r, g, b,255u)
    constructor(binary : Int) : this(
        (binary shr 16 and 0xFF).toUByte(),
        (binary shr 8 and 0xFF).toUByte(),
        (binary and 0xFF).toUByte(),
        (binary shr 24 and 0xFF).toUByte()
    )

    val value =  (b.toUInt() or (g.toUInt() shl 8) or (r.toUInt() shl 16) or (a.toUInt() shl 24)).toInt()

    fun serialize(buffer : FriendlyByteBuf) : FriendlyByteBuf {
        buffer.writeInt(value)
        return buffer
    }

    override fun toString(): String {
        return Integer.toHexString(value)
    }

    companion object{
        fun deserialize(buffer : FriendlyByteBuf) : McColor{
            return McColor(buffer.readInt())
        }

        fun fromString(hex : String) : McColor {
            return McColor(java.lang.Long.parseLong(hex, 16).toInt())
        }
    }
}

// This really looks like it could be an enum, but honestly it makes the call syntax worse.
object McColors {
    val red = McColor(255u, 0u, 0u)
    val green = McColor(0u, 255u, 0u)
    val blue = McColor(0u, 0u, 255u)
    val white = McColor(255u,255u,255u)
    val black = McColor(0u,0u,0u)
    val cyan = McColor(0u,255u,255u)
    val purple = McColor(172u,79u,198u)
    val yellow = McColor(255u, 255u, 0u)
    val lightPink = McColor(255u,182u,193u)
}

object McColorValues {
    val red = McColors.red.value
    val green = McColors.green.value
    val blue = McColors.blue.value
    val white = McColors.white.value
    val black = McColors.black.value
    val cyan = McColors.cyan.value
    val purple = McColors.purple.value
    val yellow = McColors.yellow.value
    val lightPink = McColors.lightPink.value
}
