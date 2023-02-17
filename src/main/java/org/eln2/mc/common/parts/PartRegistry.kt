package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.*
import org.eln2.mc.Eln2
import org.eln2.mc.common.items.foundation.PartItem
import org.eln2.mc.common.parts.foundation.PartProvider
import org.eln2.mc.common.parts.foundation.providers.BasicPartProvider
import org.eln2.mc.common.tabs.eln2Tab
import java.util.function.Supplier

object PartRegistry {
    private val PARTS = DeferredRegister.create<PartProvider>(Eln2.resource("parts"), Eln2.MODID)
    private val PART_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)!!

    private lateinit var partRegistry: Supplier<IForgeRegistry<PartProvider>>

    fun setup(bus: IEventBus) {
        partRegistry = PARTS.makeRegistry(PartProvider::class.java) { RegistryBuilder() }
        PARTS.register(bus)
        PART_ITEMS.register(bus)

        Eln2.LOGGER.info("Prepared part registry.")
    }

    class PartRegistryItem(
        val name: String,
        val part: RegistryObject<PartProvider>,
        val item: RegistryObject<PartItem>
    )

    /**
     * Registers everything needed to create a part.
     * This includes:
     *  - The part provider (the actual part)
     *  - The part item (used to place the part)
     *
     *  @param name The name for all the registry items.
     *  @param provider The part provider that will be used to create the part.
     * */
    private fun part(name: String, provider: PartProvider): PartRegistryItem {
        val part = PARTS.register(name) { provider }
        val item = PART_ITEMS.register(name) { PartItem(provider, eln2Tab) }

        return PartRegistryItem(name, part, item)
    }

    fun tryGetProvider(id: ResourceLocation): PartProvider? {
        return partRegistry.get().getValue(id)
    }

    fun getPartItem(id: ResourceLocation): PartItem {
        return ForgeRegistries.ITEMS.getValue(id) as PartItem
    }

    //val WIRE_PART = part("wire_part", BasicPartProvider(::WirePart, Vec3(0.1, 0.1, 0.1)))
    //val BATTERY_PART = part("battery_part", BasicPartProvider(::BatteryPart, Vec3(0.8, 0.8, 0.8)))
    val RESISTOR_PART = part("resistor_part", BasicPartProvider(::ResistorPart, Vec3(1.0, 1.0, 1.0)))
    val VOLTAGE_SOURCE_PART = part("voltage_source_part", BasicPartProvider(::VoltageSourcePart, Vec3(1.0, 1.0, 1.0)))
    val GROUND_PART = part("ground_part", BasicPartProvider(::GroundPart, Vec3(1.0, 1.0, 1.0)))
}
