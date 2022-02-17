package org.eln2.mc.common.cell

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
import org.eln2.mc.common.cell.providers.FourPinCellProvider
import org.eln2.mc.common.cell.providers.TwoPinCellProvider
import org.eln2.mc.common.cell.types.*

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object CellRegistry {
    private val CELLS = DeferredRegister.create(CellProvider::class.java, Eln2.MODID)
    private var REGISTRY : IForgeRegistry<CellProvider>? = null
    val registry get() = REGISTRY!!

    val RESISTOR_CELL = register("resistor", TwoPinCellProvider({ ResistorCell(it) }, 'R'))
    val WIRE_CELL = register("wire", FourPinCellProvider({ WireCell(it) }, 'W'))
    val VOLTAGE_SOURCE_CELL = register("voltage_source", FourPinCellProvider({ VoltageSourceCell(it) }, 'V'))
    val GROUND_CELL = register("ground", FourPinCellProvider({ GroundCell(it)}, 'G'))
    val CAPACITOR_CELL = register("capacitor", TwoPinCellProvider({ CapacitorCell(it) }, 'C'))
    val INDUCTOR_CELL = register("inductor", TwoPinCellProvider({ InductorCell(it) }, 'I'))

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
}
