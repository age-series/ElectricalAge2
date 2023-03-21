package org.eln2.mc.common.parts.foundation

import net.minecraft.nbt.CompoundTag

/**
 * Implemented by [Part]s that need to save data to the item.
 * */

enum class ItemPersistentPartLoadOrder {
    BeforeSim,
    AfterSim
}

interface IItemPersistentPart {
    val order: ItemPersistentPartLoadOrder

    fun saveItemTag(tag: CompoundTag)

    /**
     * Loads the part from the item tag.
     * @param tag The saved tag. Null if no data was present in the item (possibly because the item was newly created)
     * */
    fun loadItemTag(tag: CompoundTag?)
}
