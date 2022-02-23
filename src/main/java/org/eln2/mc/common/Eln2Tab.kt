package org.eln2.mc.common

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import org.eln2.mc.registry.CellRegistry.VOLTAGE_SOURCE_CELL

val eln2Tab: CreativeModeTab = object: CreativeModeTab("Electrical_Age") {
    override fun makeIcon(): ItemStack {
        return ItemStack(VOLTAGE_SOURCE_CELL.item())
    }
}
