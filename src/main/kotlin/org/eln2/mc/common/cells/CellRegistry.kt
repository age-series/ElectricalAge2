package org.eln2.mc.common.cells

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.IForgeRegistry
import net.minecraftforge.registries.RegistryBuilder
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.mutableBiMapOf
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.InjCellProvider
import org.eln2.mc.resource
import java.util.function.Supplier

object CellRegistry {
    private val CELLS = DeferredRegister.create<CellProvider>(resource("cells"), MODID)

    private lateinit var cellRegistry: Supplier<IForgeRegistry<CellProvider>>

    fun setup(bus: IEventBus) {
        cellRegistry = CELLS.makeRegistry { RegistryBuilder() }
        CELLS.register(bus)

        LOG.info("Prepared cell registry.")
    }

    private val cells = mutableBiMapOf<CellProvider, ResourceLocation>()

    fun getId(provider: CellProvider) = cells.forward[provider] ?: error("Failed to get cell id $provider")

    /**
     * Registers a cell using the specified ID and Provider.
     * */
    fun cell(id: String, provider: CellProvider): RegistryObject<CellProvider> {
        val result = CELLS.register(id) { provider }

        cells.add(provider, result.id)

        return result
    }

    inline fun <reified T : Cell> injCell(id: String, vararg extraParams: Any): RegistryObject<CellProvider> =
        cell(id, InjCellProvider(T::class.java, extraParams.asList()))

    /**
     * Gets the Cell Provider with the specified ID, or produces an error.
     * */
    fun getProvider(id: ResourceLocation): CellProvider {
        return cellRegistry.get().getValue(id) ?: error("Could not get cell provider with id $id")
    }
}
