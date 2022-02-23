package org.eln2.mc.registry.data

import net.minecraft.world.item.BlockItem
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.common.cell.CellBlockBase

data class CellBlockRegistryItem(
    val name : String,
    val block : RegistryObject<CellBlockBase>,
    val item : RegistryObject<BlockItem>
)
