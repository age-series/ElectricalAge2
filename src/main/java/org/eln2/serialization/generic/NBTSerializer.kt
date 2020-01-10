package org.eln2.serialization.generic

/*
NOTE: This code is strictly experimental
 */

import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList

class NBTSerializer: ISerialize {

    private val backingNBT: NBTTagCompound

    constructor() {
        backingNBT = NBTTagCompound()
    }
    constructor(nbt: NBTTagCompound) {
        backingNBT = nbt
    }

    override fun getInt(key: String): Int? {
        return backingNBT.getInteger(key)
    }
    override fun getBool(key: String): Boolean? {
        return backingNBT.getBoolean(key)
    }
    override fun getDouble(key: String): Double? {
        return backingNBT.getDouble(key)
    }
    override fun getString(key: String): String? {
        return backingNBT.getString(key)
    }

    override fun setInt(key: String, value: Int) {
        backingNBT.setInteger(key, value)
    }
    override fun setBool(key: String, value: Boolean) {
        backingNBT.setBoolean(key, value)
    }
    override fun setDouble(key: String, value: Double) {
        backingNBT.setDouble(key, value)
    }
    override fun setString(key: String, value: String) {
        backingNBT.setString(key, value)
    }

    override fun getNested(key: String): ISerialize? {
        if (backingNBT.hasKey(key))
            return NBTSerializer(backingNBT.getCompoundTag(key))
        return null
    }

    override fun setNested(key: String, value: ISerialize) {
        if (value is NBTSerializer) {
            setNested(key, value)
        } else {
            throw Error("Error! Cannot set nested when the nesting classes are not the same type!")
        }
    }

    fun setNested(key: String, value: NBTSerializer) {
        backingNBT.setTag(key, value.backingNBT)
    }
}