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
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.resource
import java.util.function.Supplier

object CellRegistry {
    private val CELLS = DeferredRegister.create<CellProvider<*>>(resource("cells"), MODID)

    private lateinit var cellRegistry: Supplier<IForgeRegistry<CellProvider<*>>>

    fun setup(bus: IEventBus) {
        cellRegistry = CELLS.makeRegistry { RegistryBuilder() }
        CELLS.register(bus)

        LOG.info("Prepared cell registry.")
    }

    private val cells = mutableBiMapOf<CellProvider<*>, ResourceLocation>()

    fun getCellId(provider: CellProvider<*>) = cells.forward[provider] ?: error("Failed to get cell id $provider")

    fun<T : Cell> cell(id: String, provider: CellProvider<T>): RegistryObject<CellProvider<T>> {
        val result = CELLS.register(id) { provider }

        cells.add(provider, result.id)

        return result
    }

    inline fun <reified T : Cell> injCell(id: String, vararg extraParams: Any): RegistryObject<CellProvider<T>> =
        cell(id, InjectCellProvider(T::class.java, extraParams.asList()))

    fun getCellProvider(id: ResourceLocation): CellProvider<*> {
        return cellRegistry.get().getValue(id) ?: error("Could not get cell provider with id $id")
    }
}
