package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.*
import org.eln2.mc.Eln2
import org.eln2.mc.common.items.PartItem
import org.eln2.mc.common.parts.part.WirePart
import org.eln2.mc.common.parts.providers.BasicPartProvider
import java.util.function.Supplier

object PartRegistry {
    private val PART_REGISTRY = DeferredRegister.create<PartProvider>(ResourceLocation("eln2:parts"), Eln2.MODID)
    private val PART_ITEM_REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)!! // Yeah, if this fails blow up the game

    private lateinit var partRegistry : Supplier<IForgeRegistry<PartProvider>>

    fun setup(bus : IEventBus) {
        partRegistry = PART_REGISTRY.makeRegistry(PartProvider::class.java){RegistryBuilder()}
        PART_REGISTRY.register(bus)
        PART_ITEM_REGISTRY.register(bus)

        Eln2.LOGGER.info("Prepared part registry.")
    }

    class PartRegistryItem(
        val name : String,
        val part : RegistryObject<PartProvider>,
        val item : RegistryObject<PartItem>
    )

    private fun part(name : String, provider : PartProvider) : PartRegistryItem {
        val part = PART_REGISTRY.register(name) { provider }
        val item = PART_ITEM_REGISTRY.register(name) { PartItem(provider) }

        return PartRegistryItem(name, part, item)
    }

    fun tryGetProvider(id : ResourceLocation) : PartProvider?{
        return partRegistry.get().getValue(id)
    }

    val WIRE_PART = part("wire_part", BasicPartProvider(::WirePart))
}
