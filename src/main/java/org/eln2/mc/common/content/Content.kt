@file:Suppress("MemberVisibilityCanBePrivate", "PublicApiImplicitType", "PublicApiImplicitType", "unused", "LongLine")

package org.eln2.mc.common.content

import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.world.phys.Vec3
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.ageseries.libage.sim.Material
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.mathematics.Functions.bbVec
import org.eln2.mc.mathematics.Functions.lerp
import org.eln2.mc.mathematics.Functions.vec3
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.providers.BasicCellProvider
import org.eln2.mc.common.containers.ContainerRegistry
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.BasicCellPart
import org.eln2.mc.common.parts.foundation.basicRenderer
import org.eln2.mc.common.parts.foundation.providers.BasicPartProvider
import org.eln2.mc.mathematics.Functions.bbSize
import org.eln2.mc.utility.SelfDescriptiveUnitMultipliers.centimeters
import org.eln2.mc.utility.SelfDescriptiveUnitMultipliers.milliOhms
import org.eln2.mc.utility.UnitConversions.kwHoursInJ
import kotlin.math.abs

/**
 * Joint registry for content classes.
 */
object Content {
    /**
     * Initializes the fields, in order to register the content.
     */
    fun initialize() {}

    //#region Wires

    val ELECTRICAL_WIRE_CELL_COPPER = CellRegistry.register("electrical_wire_cell_copper", BasicCellProvider.fourPin  { a, b ->
        WireCell(a, b, ElectricalWireModels.copper(centimeters(5.0)), WireType.Electrical)
    })

    val THERMAL_WIRE_CELL_COPPER = CellRegistry.register("thermal_wire_cell_copper", BasicCellProvider.fourPin  { a, b ->
        WireCell(a, b, ElectricalWireModels.copper(centimeters(5.0)), WireType.Thermal)
    })

    val ELECTRICAL_WIRE_PART_COPPER: PartRegistry.PartRegistryItem = PartRegistry.part("electrical_wire_part_copper", BasicPartProvider({ a, b ->
        WirePart(a, b, ELECTRICAL_WIRE_CELL_COPPER.get(), WireType.Electrical)
    }, Vec3(0.1, 0.1, 0.1)))

    val THERMAL_WIRE_PART_COPPER: PartRegistry.PartRegistryItem = PartRegistry.part("thermal_wire_part_copper", BasicPartProvider({ a, b ->
        WirePart(a, b, THERMAL_WIRE_CELL_COPPER.get(), WireType.Thermal)
    }, Vec3(0.1, 0.1, 0.1)))

    //#endregion

    val THERMAL_RADIATOR_CELL = CellRegistry.register("thermal_radiator_cell", BasicCellProvider.fourPin { a, b ->
        ThermalRadiatorCell(a, b, RadiatorModel(
            2000.0,
            100.0,
            Material.COPPER,
            100.0
        ))
    })
    val THERMAL_RADIATOR = PartRegistry.part("thermal_radiator_part", BasicPartProvider(::RadiatorPart, Vec3(1.0, 0.5, 1.0)))

    val RESISTOR_CELL = CellRegistry.register("resistor_cell", BasicCellProvider.polarFB(::ResistorCell))
    val RESISTOR_PART = PartRegistry.part("resistor_part", BasicPartProvider(::ResistorPart, Vec3(1.0, 0.4, 0.4)))

    val VOLTAGE_SOURCE_CELL = CellRegistry.register("voltage_source_cell", BasicCellProvider.monoF(::VoltageSourceCell))
    val VOLTAGE_SOURCE_PART = PartRegistry.part("voltage_source_part", BasicPartProvider(::VoltageSourcePart, Vec3(0.3, 0.3, 0.3)))

    val GROUND_CELL = CellRegistry.register("ground_cell", BasicCellProvider.monoF(::GroundCell))
    val GROUND_PART = PartRegistry.part("ground_part", BasicPartProvider(::GroundPart, Vec3(0.3, 0.3, 0.3)))

    val FURNACE_BLOCK_ENTITY = BlockRegistry.blockEntity("furnace", ::FurnaceBlockEntity) { FURNACE_BLOCK.block.get() }
    val FURNACE_CELL = CellRegistry.register("furnace_cell", BasicCellProvider.polarLR(::FurnaceCell))
    val FURNACE_BLOCK = BlockRegistry.registerBasicBlock("furnace", tab = null) { FurnaceBlock() }
    val FURNACE_MENU = ContainerRegistry.registerMenu("furnace_menu", ::FurnaceMenu)

    val BATTERY_CELL_100V = CellRegistry.register("battery_cell_t", BasicCellProvider.polarFB{ pos, id ->
        BatteryCell(pos, id, BatteryModel(
            voltageFunction = VoltageModels.WET_CELL_12V,
            resistanceFunction = { _, _ -> milliOhms(100.0) },
            damageFunction = { battery, dt ->
                val currentThreshold = 100.0 //A

                // if current > threshold, 0.0001% per second for every amp
                // if charge < threshold, amplify everything by 10delta

                val absCurrent = abs(battery.current)

                val currentTerm = if(absCurrent > currentThreshold) {
                    (absCurrent - currentThreshold) * (0.0001 / 100.0)
                } else 0.0

                val amplification = if(battery.charge < battery.model.damageChargeThreshold){
                    (battery.model.damageChargeThreshold - battery.charge) * 10
                } else 0.0

                currentTerm * dt * (1.0 + amplification)
            },
            capacityFunction = { battery ->
                // Test capacity func: 0% after 5 cycles
                // life has 90% impact.

                val lifeTerm = -(1 - battery.life) * 0.90
                val cyclesTerm = -lerp(0.0, 1.0, battery.cycles / 5.0)

                1 + lifeTerm + cyclesTerm
            },
            energyCapacity = kwHoursInJ(2.2),
            0.5,
            BatteryMaterials.PB_ACID_TEST,
            20.0,
            0.3))
        .also { it.energy = it.model.energyCapacity * 0.9 }
    })

    val BATTERY_PART_100V = PartRegistry.part("battery_part_100v", BasicPartProvider({a, b -> BatteryPart(a, b, BATTERY_CELL_100V.get())}, vec3(1.0)))

    val LIGHT_GHOST_BLOCK = BlockRegistry.registerBasicBlock("light_ghost"){GhostLightBlock()}
    val LIGHT_CELL = CellRegistry.register("light_cell", BasicCellProvider.polarLR { pos, id ->
        LightCell(pos, id, LightModels.test())
    })
    val LIGHT_PART = PartRegistry.part("light_part", BasicPartProvider({a, b -> LightPart(a, b, LIGHT_CELL.get())}, bbVec(8.0, 4.0, 5.0)))

    val THERMOCOUPLE_CELL = CellRegistry.register("thermocouple_cell", BasicCellProvider.fourPin { pos, id ->
        ThermocoupleCell(pos, id)
    })

    val THERMOCOUPLE_PART = PartRegistry.part("thermocouple_part", BasicPartProvider({id, context ->
        BasicCellPart(id, context, Vec3(1.0, 15.0 / 16.0, 1.0), THERMOCOUPLE_CELL.get(), basicRenderer(PartialModels.PELTIER_ELEMENT, bbOffset(15.0)))
    }, Vec3(0.5, 15.0 / 16.0, 0.5)))

    val HEAT_GENERATOR_CELL = CellRegistry.register("heat_generator_cell", BasicCellProvider.fourPin(::HeatGeneratorCell))
    val HEAT_GENERATOR_PART = PartRegistry.part("heat_generator_part", BasicPartProvider( { id, context ->
        BasicCellPart(id, context, vec3(1.0), HEAT_GENERATOR_CELL.get(), basicRenderer(PartialModels.THERMAL_WIRE_CROSSING_FULL, 0.0))
    }, vec3(1.0)))
    val HEAT_GENERATOR_BLOCK = BlockRegistry.registerBasicBlock("heat_generator", tab = null) { HeatGeneratorBlock() }
    val HEAT_GENERATOR_BLOCK_ENTITY = BlockRegistry.blockEntity("heat_generator", ::HeatGeneratorBlockEntity) { HEAT_GENERATOR_BLOCK.block.get() }

    val PHOTOVOLTAIC_GENERATOR_CELL = CellRegistry.register("photovoltaic_cell", BasicCellProvider.polarFB { pos, id ->
        PhotovoltaicGeneratorCell(pos, id, PhotovoltaicModels.test24Volts())
    })

    val PHOTOVOLTAIC_PANEL_PART = PartRegistry.part("photovoltaic_panel_part", BasicPartProvider({id, context ->
        BasicCellPart(id, context, Vec3(1.0, bbSize(2.0), 1.0), PHOTOVOLTAIC_GENERATOR_CELL.get(), basicRenderer(PartialModels.SOLAR_PANEL_ONE_BLOCK, bbOffset(2.0)))
    }, Vec3(1.0, bbSize(2.0), 1.0)))

    @Mod.EventBusSubscriber
    object ClientSetup {
        @SubscribeEvent
        fun clientSetup(event: FMLClientSetupEvent) {
            event.enqueueWork {
                clientWork()

                LOGGER.info("Content registry client-sided setup complete.")
            }
        }

        private fun clientWork() {
            MenuScreens.register(FURNACE_MENU.get(), ::FurnaceScreen)
        }
    }
}
