package org.eln2.mc.common.cells

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.*
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.providers.BasicCellProvider
import org.eln2.mc.common.content.*
import java.util.function.Supplier

object CellRegistry {
    private val CELLS = DeferredRegister.create<CellProvider>(Eln2.resource("cells"), Eln2.MODID)

    private lateinit var cellRegistry: Supplier<IForgeRegistry<CellProvider>>

    fun setup(bus: IEventBus) {
        cellRegistry = CELLS.makeRegistry(CellProvider::class.java) { RegistryBuilder() }
        CELLS.register(bus)

        LOGGER.info("Prepared cell registry.")
    }

    /**
     * Registers a cell using the specified ID and Provider.
     * */
    private fun register(id: String, provider: CellProvider): RegistryObject<CellProvider> {
        return CELLS.register(id) { provider }
    }

    /**
     * Gets the Cell Provider with the specified ID, or produces an error.
     * */
    fun getProvider(id: ResourceLocation): CellProvider {
        return cellRegistry.get().getValue(id) ?: error("Could not get cell provider with id $id")
    }

    val RESISTOR_CELL = register("resistor_cell", BasicCellProvider.polar(::ResistorCell))
    val VOLTAGE_SOURCE_CELL = register("voltage_source_cell", BasicCellProvider.fourPin(::VoltageSourceCell))
    val GROUND_CELL = register("ground_cell", BasicCellProvider.fourPin(::GroundCell))
    val WIRE_CELL = register("wire_cell", BasicCellProvider.fourPin(::WireCell))
    val FURNACE_CELL = register("furnace_cell", BasicCellProvider.polar(::FurnaceCell))
}
