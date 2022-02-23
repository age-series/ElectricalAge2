package org.eln2.mc.registry.data

import net.minecraft.world.item.Item
import net.minecraftforge.registries.RegistryObject

data class ItemRegistryItem(
    val name: String,
    val item: RegistryObject<Item>
)
