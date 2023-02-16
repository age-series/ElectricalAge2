package org.eln2.mc.common.tabs

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import org.eln2.mc.common.blocks.BlockRegistry

val eln2Tab: CreativeModeTab = object : CreativeModeTab("Electrical_Age") {
    override fun makeIcon(): ItemStack {
        return ItemStack(BlockRegistry.VOLTAGE_SOURCE_CELL.item.get().asItem())
    }
}
