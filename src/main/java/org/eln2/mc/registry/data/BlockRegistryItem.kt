package org.eln2.mc.registry.data

import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block
import net.minecraftforge.registries.RegistryObject

data class BlockRegistryItem(
    val name: String,
    val block: RegistryObject<Block>,
    val item: RegistryObject<BlockItem>
)
