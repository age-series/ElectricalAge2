package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.registries.*
import org.eln2.mc.Eln2
import java.util.function.Supplier

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object PartRegistry {
    private val PARTS = DeferredRegister.create(PartProvider::class.java, Eln2.MODID)
    private var REGISTRY : Supplier<IForgeRegistry<PartProvider>?>? = null
    val registry get() = REGISTRY!!.get()!!

    fun setup(bus : IEventBus) {
        PARTS.register(bus)
        Eln2.LOGGER.info("Prepared part registry.")
    }

    @SubscribeEvent
    fun createRegistry(event : NewRegistryEvent) {
        val reg = RegistryBuilder<PartProvider>()
        reg.setName(ResourceLocation(Eln2.MODID, "parts"))
        reg.type = PartProvider::class.java
        REGISTRY = event.create(reg)
        Eln2.LOGGER.info("Created part registry!")
    }

    private fun register(id : String, provider: PartProvider) : RegistryObject<PartProvider> {
        return PARTS.register(id) { provider }
    }

    fun getProvider(id : ResourceLocation) : PartProvider {
        return registry.getValue(id) ?: error("Could not get part provider with id $id")
    }
}
