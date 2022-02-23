package org.eln2.mc.registry

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.IForgeRegistry
import net.minecraftforge.registries.RegistryBuilder
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cell.CellProvider
import org.eln2.mc.common.cell.providers.FourPinCellProvider
import org.eln2.mc.common.cell.providers.NoPinCellProvider
import org.eln2.mc.common.cell.providers.TwoPinCellProvider
import org.eln2.mc.content.cell.battery.BatteryCell
import org.eln2.mc.content.cell.capacitor.CapacitorCell
import org.eln2.mc.content.cell.diode.DiodeCell
import org.eln2.mc.content.cell.ground.GroundCell
import org.eln2.mc.content.cell.inductor.InductorCell
import org.eln2.mc.content.cell.light.LightCell
import org.eln2.mc.content.cell.resistor.ResistorCell
import org.eln2.mc.content.cell.solar_light.SolarLightCell
import org.eln2.mc.content.cell.solar_panel.SolarPanelCell
import org.eln2.mc.content.cell.voltage_source.VoltageSourceCell
import org.eln2.mc.content.cell.wire.WireCell

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
    fun createRegistry(event : RegistryEvent.NewRegistry) {
        val reg = RegistryBuilder<CellProvider>()
        reg.setName(ResourceLocation(Eln2.MODID, "cells"))
        reg.type = CellProvider::class.java
        REGISTRY = reg.create()
        LOGGER.info("Created cell registry!")
    }

    private fun register(id : String, provider: CellProvider) : RegistryObject<CellProvider> {
        return CELLS.register(id) { provider }
    }

    /*
     * Cell Registry Begins Here
     */
    val RESISTOR_CELL = register("resistor", TwoPinCellProvider { ResistorCell(it) })
    val WIRE_CELL = register("wire", FourPinCellProvider { WireCell(it) })
    val VOLTAGE_SOURCE_CELL = register("voltage_source", FourPinCellProvider { VoltageSourceCell(it) })
    val GROUND_CELL = register("ground", FourPinCellProvider { GroundCell(it) })
    val CAPACITOR_CELL = register("capacitor", TwoPinCellProvider { CapacitorCell(it) })
    val INDUCTOR_CELL = register("inductor", TwoPinCellProvider { InductorCell(it) })
    val DIODE_CELL = register("diode", TwoPinCellProvider { DiodeCell(it) })
    val BATTERY_CELL = register("12v_battery", TwoPinCellProvider { BatteryCell(it) })
    val LIGHT_CELL = register("light", FourPinCellProvider { LightCell(it) })
    val SOLAR_LIGHT_CELL = register("solar_light", NoPinCellProvider { SolarLightCell(it) })
    val SOLAR_PANEL_CELL = register("solar_panel", TwoPinCellProvider { SolarPanelCell(it) })
}
