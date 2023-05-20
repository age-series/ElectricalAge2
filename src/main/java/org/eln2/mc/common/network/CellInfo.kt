package org.eln2.mc.common.network

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import org.eln2.mc.extensions.readString
import org.eln2.mc.extensions.readStringMap
import org.eln2.mc.extensions.writeString
import org.eln2.mc.extensions.writeStringMap

class CellInfo(val type: String, val info: Map<String, String>, val pos: BlockPos) {
    constructor(buffer: FriendlyByteBuf) : this(buffer.readString(), buffer.readStringMap(), buffer.readBlockPos())

    fun serialize(buffer: FriendlyByteBuf) {
        buffer.writeString(type)
        buffer.writeStringMap(info)
        buffer.writeBlockPos(pos)
    }
}
