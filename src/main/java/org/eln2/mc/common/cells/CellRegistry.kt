package org.eln2.mc.common.cells

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.*
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.providers.FourPinCellProvider
import org.eln2.mc.common.cells.foundation.providers.NoPinCellProvider
import org.eln2.mc.common.cells.foundation.providers.TwoPinCellProvider
import java.util.function.Supplier

object CellRegistry {
    private val CELLS = DeferredRegister.create<CellProvider>(Eln2.resource("cells"), Eln2.MODID)

    private lateinit var cellRegistry: Supplier<IForgeRegistry<CellProvider>>

    fun setup(bus: IEventBus) {
        cellRegistry = CELLS.makeRegistry(CellProvider::class.java) { RegistryBuilder() }
        CELLS.register(bus)

        LOGGER.info("Prepared cell registry.")
    }

    private fun register(id: String, provider: CellProvider): RegistryObject<CellProvider> {
        return CELLS.register(id) { provider }
    }

    fun getProvider(id: ResourceLocation): CellProvider {
        return cellRegistry.get().getValue(id) ?: error("Could not get cell provider with id $id")
    }

    val RESISTOR_CELL = register("resistor", TwoPinCellProvider { ResistorCell(it) })
    val WIRE_CELL = register("wire", FourPinCellProvider { WireCell(it) })
    val VOLTAGE_SOURCE_CELL = register("voltage_source", FourPinCellProvider { VoltageSourceCell(it) })
    val GROUND_CELL = register("ground", FourPinCellProvider { GroundCell(it) })
    val CAPACITOR_CELL = register("capacitor", TwoPinCellProvider { CapacitorCell(it) })
    val INDUCTOR_CELL = register("inductor", TwoPinCellProvider { InductorCell(it) })
    val DIODE_CELL = register("diode", TwoPinCellProvider { DiodeCell(it) })
    val BATTERY_CELL = register("12v_battery", TwoPinCellProvider { BatteryCell(it) })
    val LIGHT_CELL = register("light", FourPinCellProvider { LightCell(it) })
    val SOLAR_LIGHT_CELL = register("solar_light", NoPinCellProvider { SolarLightCell(it) })
    val SOLAR_PANEL_CELL = register("solar_panel", TwoPinCellProvider { SolarPanelCell(it) })
}
