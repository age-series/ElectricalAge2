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
import org.apache.logging.log4j.LogManager
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cell.types.TestCell

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object CellRegistry {
    private val CELLS = DeferredRegister.create(CellProvider::class.java, Eln2.MODID)
    private var REGISTRY : IForgeRegistry<CellProvider>? = null
    val registry get() = REGISTRY!!

    val TEST_CELL = register("test", TestCell.Provider())

    fun setup(bus : IEventBus) {
        CELLS.register(bus)
        LOGGER.info("Prepared cell registry queue.")
    }

    @SubscribeEvent
    fun createRegistry(event : RegistryEvent.NewRegistry) {
        val reg = RegistryBuilder<CellProvider>()
        reg.setName(ResourceLocation(Eln2.MODID, "cells"))
        reg.type = CellProvider::class.java
        REGISTRY = reg.create()
        LOGGER.info("Prepared cell registry!")
    }

    private fun register(id : String, provider: CellProvider) : RegistryObject<CellProvider> {
        return CELLS.register(id) { provider }
    }
}
