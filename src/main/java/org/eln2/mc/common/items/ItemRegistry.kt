@file:Suppress("unused") // Because block variables here would be suggested for deletion.

package org.eln2.mc.common.items

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.blocks.foundation.MultiblockScanTool

object ItemRegistry {
    @Suppress("MemberVisibilityCanBePrivate") // Used for item registration and fetching
    val REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)!! // Yeah, if this fails blow up the game

    fun setup(bus: IEventBus) {
        REGISTRY.register(bus)
        LOGGER.info("Prepared item registry.")
    }

    data class ItemRegistryItem(
        val name: String,
        val item: RegistryObject<Item>
    )

    fun item(name: String, supplier: () -> Item): ItemRegistryItem {
        val item = REGISTRY.register(name) { supplier() }
        return ItemRegistryItem(name, item)
    }

    val MULTIBLOCK_SCAN_TOOL = item("multiblock_scan_tool", ::MultiblockScanTool)
}

val eln2Tab: CreativeModeTab = object : CreativeModeTab("Electrical_Age") {
    override fun makeIcon(): ItemStack {
        return ItemStack(Blocks.BARRIER.asItem())
    }
}
