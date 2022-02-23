package org.eln2.mc.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.IForgeRegistry
import net.minecraftforge.registries.RegistryBuilder
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cell.CellBlockBase
import org.eln2.mc.common.cell.CellProvider
import org.eln2.mc.common.cell.providers.FourPinCellProvider
import org.eln2.mc.common.cell.providers.NoPinCellProvider
import org.eln2.mc.common.cell.providers.TwoPinCellProvider
import org.eln2.mc.common.eln2Tab
import org.eln2.mc.content.cell.battery.BatteryCell
import org.eln2.mc.content.cell.battery.BatteryCellBlock
import org.eln2.mc.content.cell.capacitor.CapacitorCell
import org.eln2.mc.content.cell.capacitor.CapacitorCellBlock
import org.eln2.mc.content.cell.diode.DiodeCell
import org.eln2.mc.content.cell.diode.DiodeCellBlock
import org.eln2.mc.content.cell.ground.GroundCell
import org.eln2.mc.content.cell.ground.GroundCellBlock
import org.eln2.mc.content.cell.inductor.InductorCell
import org.eln2.mc.content.cell.inductor.InductorCellBlock
import org.eln2.mc.content.cell.light.LightCell
import org.eln2.mc.content.cell.light.LightCellBlock
import org.eln2.mc.content.cell.resistor.ResistorCell
import org.eln2.mc.content.cell.resistor.ResistorCellBlock
import org.eln2.mc.content.cell.solar_light.SolarLightCell
import org.eln2.mc.content.cell.solar_light.SolarLightCellBlock
import org.eln2.mc.content.cell.solar_panel.SolarPanelCell
import org.eln2.mc.content.cell.solar_panel.SolarPanelCellBlock
import org.eln2.mc.content.cell.voltage_source.VoltageSourceCell
import org.eln2.mc.content.cell.voltage_source.VoltageSourceCellBlock
import org.eln2.mc.content.cell.wire.WireCell
import org.eln2.mc.content.cell.wire.WireCellBlock
import org.eln2.mc.registry.data.CellBlockRegistryItem
import org.eln2.mc.registry.data.CellRegistryItem

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object CellRegistry {
    private val CELLS = DeferredRegister.create(CellProvider::class.java, Eln2.MODID)
    private var REGISTRY : IForgeRegistry<CellProvider>? = null
    val registry get() = REGISTRY!!

    fun setup(bus : IEventBus) {
        CELLS.register(bus)
        LOGGER.info("Prepared cell registry.")
    }

    @SubscribeEvent
    fun createRegistry( @Suppress("UNUSED_PARAMETER") event : RegistryEvent.NewRegistry ) {
        val reg = RegistryBuilder<CellProvider>()
        reg.setName(ResourceLocation(Eln2.MODID, "cells"))
        reg.type = CellProvider::class.java
        REGISTRY = reg.create()
        LOGGER.info("Created cell registry!")
    }

    private fun registerCellBlock(name : String, tab: CreativeModeTab? = null, supplier : () -> CellBlockBase) : CellBlockRegistryItem {
        val block = BlockRegistry.BLOCK_REGISTRY.register(name) { supplier() }
        val item = BlockRegistry.BLOCK_ITEM_REGISTRY.register(name) { BlockItem(block.get(), Item.Properties().also {if (tab != null) it.tab(tab)}) }
        return CellBlockRegistryItem(name, block, item)
    }

    private fun register(name : String, provider: CellProvider, supplier: () -> CellBlockBase, tab: CreativeModeTab? = null) : CellRegistryItem {
        val block = registerCellBlock(name, tab, supplier)
        return CellRegistryItem(name, CELLS.register(name) { provider }, block.block, block.item)
    }

    /*
     * Cell Registry Begins Here
     */
    val RESISTOR_CELL = register("resistor", TwoPinCellProvider { ResistorCell(it) }, { ResistorCellBlock() }, eln2Tab)
    val WIRE_CELL = register("wire", FourPinCellProvider { WireCell(it) }, { WireCellBlock() }, eln2Tab)
    val VOLTAGE_SOURCE_CELL = register("voltage_source", FourPinCellProvider { VoltageSourceCell(it) }, { VoltageSourceCellBlock() }, eln2Tab)
    val GROUND_CELL = register("ground", FourPinCellProvider { GroundCell(it) }, { GroundCellBlock() }, eln2Tab)
    val CAPACITOR_CELL = register("capacitor", TwoPinCellProvider { CapacitorCell(it) }, { CapacitorCellBlock() }, eln2Tab)
    val INDUCTOR_CELL = register("inductor", TwoPinCellProvider { InductorCell(it) }, { InductorCellBlock() }, eln2Tab)
    val DIODE_CELL = register("diode", TwoPinCellProvider { DiodeCell(it) }, { DiodeCellBlock() }, eln2Tab)
    val BATTERY_CELL = register("battery", TwoPinCellProvider { BatteryCell(it) }, { BatteryCellBlock() }, eln2Tab)
    val LIGHT_CELL = register("light", FourPinCellProvider { LightCell(it) }, { LightCellBlock() })
    val SOLAR_LIGHT_CELL = register("solar_light", NoPinCellProvider { SolarLightCell(it) }, { SolarLightCellBlock() })
    val SOLAR_PANEL_CELL = register("solar_panel", TwoPinCellProvider { SolarPanelCell(it) }, { SolarPanelCellBlock() }, eln2Tab)
}
