package org.eln2.mc.extensions

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag

object NbtExtensions {
    fun CompoundTag.putBlockPos(key : String, pos : BlockPos) {
        val dataTag = CompoundTag()
        dataTag.putInt("X", pos.x)
        dataTag.putInt("Y", pos.y)
        dataTag.putInt("Z", pos.z)
        this.put(key, dataTag)
    }

    fun CompoundTag.getBlockPos(key : String) : BlockPos {
        val dataTag = this.get(key) as CompoundTag
        val x = dataTag.getInt("X")
        val y = dataTag.getInt("Y")
        val z = dataTag.getInt("Z")

        return BlockPos(x, y, z)
    }
}
