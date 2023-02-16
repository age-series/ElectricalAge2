package org.eln2.mc.extensions

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.parts.foundation.PartUpdateType

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

    fun CompoundTag.putCellPos(key : String, pos : CellPos){
        val dataTag = CompoundTag()
        dataTag.putBlockPos("Pos", pos.blockPos)
        dataTag.setDirection("Face", pos.face)
        this.put(key, dataTag)
    }

    fun CompoundTag.getCellPos(key : String) : CellPos {
        val dataTag = this.get(key) as CompoundTag
        val blockPos = dataTag.getBlockPos("Pos")
        val face = dataTag.getDirection("Face")

        return CellPos(blockPos, face)
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

    fun CompoundTag.setResourceLocation(key : String, resourceLocation: ResourceLocation){
        this.putString(key, resourceLocation.toString())
    }

    fun CompoundTag.tryGetResourceLocation(key : String) : ResourceLocation?{
        val str = this.getString(key)

        return ResourceLocation.tryParse(str)
    }

    fun CompoundTag.getResourceLocation(key : String) : ResourceLocation{
        return this.tryGetResourceLocation(key) ?: error("Invalid resource location with key $key")
    }

    fun CompoundTag.setDirection(key : String, direction: Direction){
        this.putInt(key, direction.get3DDataValue())
    }

    fun CompoundTag.getDirection(key : String) : Direction{
        val data3d = this.getInt(key)

        return Direction.from3DDataValue(data3d)
    }

    fun CompoundTag.setRelativeDirection(key : String, direction : RelativeRotationDirection){
        val data = direction.id

        this.putInt(key, data)
    }

    fun CompoundTag.getRelativeDirection(key : String) : RelativeRotationDirection {
        val data = this.getInt(key)

        return RelativeRotationDirection.fromId(data)
    }

    fun CompoundTag.setPartUpdateType(key : String, type : PartUpdateType){
        val data = type.id

        this.putInt(key, data)
    }

    fun CompoundTag.getPartUpdateType(key : String) : PartUpdateType {
        val data = this.getInt(key)

        return PartUpdateType.fromId(data)
    }
}
