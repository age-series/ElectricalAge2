package org.eln2.mc.registry.data

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.common.cell.CellBlockBase
import org.eln2.mc.common.cell.CellProvider

data class CellRegistryItem(
    val name: String,
    val backingCell: RegistryObject<CellProvider>,
    val backingItem: RegistryObject<CellBlockBase>,
    val backingBlock: RegistryObject<BlockItem>
) {
    fun item(): Item = backingItem.get().asItem()?: error("Tried to get backing item before registry valid")
    fun cellId(): ResourceLocation = backingCell.id?: error("Tried to get backing cell ID before registry valid")
}
