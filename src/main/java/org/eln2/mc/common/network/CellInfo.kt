package org.eln2.mc.common.network

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import org.eln2.mc.extensions.ByteBufferExtensions.readString
import org.eln2.mc.extensions.ByteBufferExtensions.readStringMap
import org.eln2.mc.extensions.ByteBufferExtensions.writeString
import org.eln2.mc.extensions.ByteBufferExtensions.writeStringMap

class CellInfo(val type: String, val info: Map<String, String>, val pos: BlockPos) {
    constructor(buffer: FriendlyByteBuf) : this(buffer.readString(), buffer.readStringMap(), buffer.readBlockPos())

    fun serialize(buffer: FriendlyByteBuf) {
        buffer.writeString(type)
        buffer.writeStringMap(info)
        buffer.writeBlockPos(pos)
    }
}
