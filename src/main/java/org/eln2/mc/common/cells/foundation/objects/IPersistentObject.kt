package org.eln2.mc.common.cells.foundation.objects

import net.minecraft.nbt.CompoundTag

interface IPersistentObject {
    fun save(): CompoundTag
    fun load(tag: CompoundTag)
}
