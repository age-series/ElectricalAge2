package org.eln2.mc.utility

import net.minecraft.network.FriendlyByteBuf

class McColor(val r : UByte, val g : UByte, val b : UByte, val a : UByte) {
    constructor(r : UByte, g : UByte, b : UByte) : this(r, g, b,255u)
    constructor(binary : Int) : this(
        (binary shr 16 and 0xFF).toUByte(),
        (binary shr 8 and 0xFF).toUByte(),
        (binary and 0xFF).toUByte(),
        (binary shr 24 and 0xFF).toUByte()
    )

    val value =  (b.toUInt() or (g.toUInt() shl 8) or (r.toUInt() shl 16) or (a.toUInt() shl 24)).toInt()

    fun serialize(buffer : FriendlyByteBuf) : FriendlyByteBuf{
        buffer.writeInt(value)
        return buffer
    }

    companion object{
        fun deserialize(buffer : FriendlyByteBuf) : McColor{
            return McColor(buffer.readInt())
        }
    }
}

object McColors {
    val red = McColor(255u, 0u, 0u)
    val green = McColor(0u, 255u, 0u)
    val blue = McColor(0u, 0u, 255u)
    val white = McColor(255u,255u,255u)
    val black = McColor(0u,0u,0u)
    val cyan = McColor(0u,255u,255u)
    val purple = McColor(172u,79u,198u)
}

object McColorValues {
    val red = McColors.red.value
    val green = McColors.green.value
    val blue = McColors.blue.value
    val white = McColors.white.value
    val black = McColors.black.value
    val cyan = McColors.cyan.value
    val purple = McColors.purple.value
}
