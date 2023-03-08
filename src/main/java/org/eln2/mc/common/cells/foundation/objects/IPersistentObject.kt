package org.eln2.mc.common.cells.foundation.objects

import net.minecraft.nbt.CompoundTag

/**
 * Represents an object with NBT saving capabilities.
 * */
interface IPersistentObject {
    fun save(): CompoundTag
    fun load(tag: CompoundTag)
}
