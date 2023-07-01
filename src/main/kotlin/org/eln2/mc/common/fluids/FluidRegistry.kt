package org.eln2.mc.common.fluids

import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.mc.MODID

object FluidRegistry {
    val FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, MODID)
    val FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, MODID)

    fun setup(bus: IEventBus) {
        FLUIDS.register(bus)
        FLUID_TYPES.register(bus)
    }
}
