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

    fun CompoundTag.getStringList(key: String): List<String> {
        val tag = this.getCompound(key)
        return tag.allKeys.map {tag.getString(it)}
    }

    fun CompoundTag.setStringList(key: String, list: List<String>) {
        val tag = CompoundTag()
        list.forEachIndexed { index, it ->
            tag.putString("$index", it)
        }
        this.put(key, tag)
    }

    fun CompoundTag.getStringMap(key: String): Map<String, String> {
        val tag = this.getCompound(key)
        val map = mutableMapOf<String, String>()
        tag.allKeys.forEach {
                tagKey ->
            val tagValue = tag.getString(tagKey)
            map[tagKey] = tagValue
        }
        return map
    }

    fun CompoundTag.setStringMap(key: String, map: Map<String, String>) {
        val tag = CompoundTag()
        map.forEach { (k, v) ->
            tag.putString(k, v)
        }
        this.put(key, tag)
    }
}
