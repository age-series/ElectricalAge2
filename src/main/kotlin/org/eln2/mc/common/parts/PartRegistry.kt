package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.*
import org.ageseries.libage.data.mutableBiMapOf
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.common.items.foundation.PartItem
import org.eln2.mc.common.parts.foundation.PartProvider
import org.eln2.mc.resource
import java.util.function.Supplier

object PartRegistry {
    private val PARTS = DeferredRegister.create<PartProvider>(resource("parts"), MODID)
    private val PART_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID)!!

    private lateinit var partRegistry: Supplier<IForgeRegistry<PartProvider>>

    fun setup(bus: IEventBus) {
        partRegistry = PARTS.makeRegistry { RegistryBuilder() }
        PARTS.register(bus)
        PART_ITEMS.register(bus)

        LOG.info("Prepared part registry.")
    }

    class PartRegistryItem(
        val name: String,
        val part: RegistryObject<PartProvider>,
        val item: RegistryObject<PartItem>,
    )

    private val parts = mutableBiMapOf<PartProvider, ResourceLocation>()

    fun getId(provider: PartProvider) = parts.forward[provider] ?: error("Failed to get part id $provider")

    /**
     * Registers everything needed to create a part.
     * This includes:
     *  - The part provider (the actual part)
     *  - The part item (used to place the part)
     *
     *  @param name The name for all the registry items.
     *  @param provider The part provider that will be used to create the part.
     * */
    fun part(name: String, provider: PartProvider): PartRegistryItem {
        val part = PARTS.register(name) { provider }
        val item = PART_ITEMS.register(name) { PartItem(provider) }

        parts.add(provider, part.id)

        return PartRegistryItem(name, part, item)
    }

    /**
     * Gets the Part Provider with the specified ID, or null, if it does not exist.
     * */
    fun tryGetProvider(id: ResourceLocation): PartProvider? {
        return partRegistry.get().getValue(id)
    }

    /**
     * Gets the Part Item of the Part with the specified ID.
     * */
    fun getPartItem(id: ResourceLocation): PartItem {
        return ForgeRegistries.ITEMS.getValue(id) as PartItem
    }
}
