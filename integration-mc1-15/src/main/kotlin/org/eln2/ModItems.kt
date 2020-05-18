package org.eln2

import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import org.eln2.items.MultimeterItem
import org.eln2.items.OreChunks

/**
 * Items added here are automatically registered.
 */
enum class ModItems(val items: Item) {
	MULTIMETER(MultimeterItem()),
	ORE_CHUNKS_COPPER(OreChunks())
}
