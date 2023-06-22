package org.eln2.mc.common.fluids

import net.minecraft.world.level.material.Fluid
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.MODID

object FluidRegistry {
    val FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, MODID)

    fun setup(bus: IEventBus) {
        FLUIDS.register(bus)
    }

    data class FluidRegistryItem(
        val name: String,
        val fluid: RegistryObject<Fluid>,
    )

    fun fluid(name: String, supplier: () -> Fluid): FluidRegistryItem {
        val fluid = FLUIDS.register(name) { supplier() }

        return FluidRegistryItem(
            name,
            fluid
        )
    }
}
