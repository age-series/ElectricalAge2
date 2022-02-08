package org.eln2.mc.common.items

import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER

object ItemRegistry {
    private val REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)

    fun setup(bus : IEventBus){
        REGISTRY.register(bus)
        LOGGER.info("Prepared item registry.")
    }
}
