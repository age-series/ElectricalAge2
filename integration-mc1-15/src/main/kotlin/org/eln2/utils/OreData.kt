package org.eln2.utils

import net.minecraft.item.ItemStack

/**
 * OreData
 * Data about the ores you are trying to make.
 *
 * @param hardness How hard is the block (pickaxe type basically)
 * @param name Name of the ore (eg, "native_copper")
 * @param result The item to drop
 */
data class OreData(val hardness: Float, val name: String, val result: ItemStack)
