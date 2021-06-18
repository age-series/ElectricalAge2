package org.eln2.utils

import net.minecraft.item.ItemStack

/**
 * OreData
 * Data about the ores you are trying to make.
 *
 * @param hardness How hard is the block (pickaxe type basically)
 * @param name Name of the ore (eg, "native_copper")
 * @param rarity Rarity of the ore (eg, ->
 * Very Rare 1 (times 2 for ore vein size, times 25 for world spawn height)
 * Rare 2
 * Uncommon 3
 * Common 4
 * Very Common 5)
 * @param result The item to drop
 */
data class OreData(val hardness: Int, val name: String, val rarity: Int, val result: ItemStack)
