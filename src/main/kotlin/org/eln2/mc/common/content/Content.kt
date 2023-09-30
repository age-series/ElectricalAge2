@file:Suppress("MemberVisibilityCanBePrivate", "PublicApiImplicitType", "PublicApiImplicitType", "unused", "LongLine")

package org.eln2.mc.common.content

import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.Material
import org.eln2.mc.LOG
import org.eln2.mc.ThermalBodyDef
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.client.render.foundation.defaultRadiantBodyColor
import org.eln2.mc.common.blocks.BlockRegistry.block
import org.eln2.mc.common.blocks.BlockRegistry.blockEntity
import org.eln2.mc.common.cells.CellRegistry.cell
import org.eln2.mc.common.cells.CellRegistry.injCell
import org.eln2.mc.common.cells.foundation.BasicCellProvider
import org.eln2.mc.common.cells.foundation.TemperatureExplosionBehaviorOptions
import org.eln2.mc.common.containers.ContainerRegistry.menu
import org.eln2.mc.common.items.ItemRegistry.item
import org.eln2.mc.common.parts.PartRegistry.part
import org.eln2.mc.common.parts.foundation.BasicCellPart
import org.eln2.mc.common.parts.foundation.BasicPartProvider
import org.eln2.mc.common.parts.foundation.basicPartRenderer
import org.eln2.mc.data.*
import org.eln2.mc.formatted
import org.eln2.mc.mathematics.*
import org.eln2.mc.measureDuration

/**
 * Joint registry for content classes.
 */
object Content {
    /**
     * Initializes the fields, in order to register the content.
     */
    fun initialize() {}

    //#region Wires

    val COPPER_THERMAL_WIRE = ThermalWireBuilder("thermal_wire_copper")
        .apply {
            damageOptions = TemperatureExplosionBehaviorOptions(temperatureThreshold = Quantity(1000.0, CELSIUS))
        }
        .register()

    val ELECTRICAL_WIRE_COPPER = ElectricalWireBuilder("electrical_cable_copper")
        .apply {
            radiatesLight = false
        }
        .register()

    val VOLTAGE_SOURCE_CELL = cell("voltage_source_cell", BasicCellProvider(::VoltageSourceCell))
    val VOLTAGE_SOURCE_PART = part("voltage_source_part", BasicPartProvider(::VoltageSourcePart, Vec3(0.3, 0.3, 0.3)))

    val GROUND_CELL = cell("ground_cell", BasicCellProvider(::GroundCell))
    val GROUND_PART = part("ground_part", BasicPartProvider(::GroundPart, Vec3(0.3, 0.3, 0.3)))

    val BATTERY_CELL_100V = cell("battery_cell_t", BasicCellProvider { createInfo ->
        BatteryCell(
            createInfo, BatteryModels.LEAD_ACID_12V
        ).also { it.energy = it.model.energyCapacity * 0.9 }
    })

    val BATTERY_PART_100V =
        part(
            "battery_part_100v",
            BasicPartProvider({ a, b ->
                BatteryPart(a, b, BATTERY_CELL_100V.get(), bbVec(6.0, 8.0, 12.0)) { part ->
                    BasicPartRenderer(part, PartialModels.BATTERY).also {
                        it.downOffset = bbOffset(8.0)
                    }
                }
            }, vec3(1.0))
        )

    val RESISTOR_CELL = cell("resistor_cell", BasicCellProvider(::ResistorCell))
    val RESISTOR_PART = part("resistor_part", BasicPartProvider(::ResistorPart, Vec3(1.0, 0.4, 0.4)))

    val THERMAL_RADIATOR_CELL = injCell<ThermalWireCell>(
        "thermal_radiator_cell",
        WireThermalProperties(
            ThermalBodyDef(Material.COPPER, 10.0, 50.0),
            TemperatureExplosionBehaviorOptions(temperatureThreshold = Quantity(1000.0, CELSIUS)),
            true
        )
    )
    val THERMAL_RADIATOR = part("thermal_radiator_part", BasicPartProvider({ id, ctx ->
        RadiatorPart(id, ctx, defaultRadiantBodyColor()) }, Vec3(1.0, 3.0 / 16.0, 1.0)))

    val HEAT_GENERATOR_CELL = injCell<HeatGeneratorCell>(
        "heat_generator_cell",
        ThermalBodyDef(
            Material.COPPER,
            10.0,
            6.0
        )
    )
    val HEAT_GENERATOR_BLOCK = block("heat_generator", tab = null) { HeatGeneratorBlock() }
    val HEAT_GENERATOR_BLOCK_ENTITY = blockEntity("heat_generator", ::HeatGeneratorBlockEntity) { HEAT_GENERATOR_BLOCK.block.get() }
    val HEAT_GENERATOR_MENU = menu("heat_generator_menu", ::HeatGeneratorMenu)


    val ELECTRICAL_ENERGY_METER_CELL = injCell<ElectricalEnergyMeterCell>("electrical_energy_meter_cell")
    val ELECTRICAL_ENERGY_METER_PART = part("electrical_energy_meter_part", BasicPartProvider(::ElectricalEnergyMeterPart, Vec3(1.0, 1.0, 1.0)))


    val PHOTOVOLTAIC_GENERATOR_CELL = cell("photovoltaic_cell", BasicCellProvider {
        PhotovoltaicGeneratorCell(it, PhotovoltaicModels.test24Volts())
    })

    val PHOTOVOLTAIC_PANEL_PART = part("photovoltaic_panel_part", BasicPartProvider({ id, context ->
        BasicCellPart(
            id,
            context,
            Vec3(1.0, bbSize(2.0), 1.0),
            PHOTOVOLTAIC_GENERATOR_CELL.get(),
            basicPartRenderer(
                PartialModels.SOLAR_PANEL_ONE_BLOCK,
                bbOffset(2.0)
            )
        )
    }, Vec3(1.0, bbSize(2.0), 1.0)))

    val LIGHT_CELL = cell("light_cell", BasicCellProvider { context ->
        LightCell(context, directionPoleMap(Base6Direction3d.Left, Base6Direction3d.Right)).also { cell ->
            cell.ruleSet.withDirectionRule(Base6Direction3dMask.LEFT + Base6Direction3dMask.RIGHT)
        }
    })

    val OSCILLATOR_CELL = injCell<OscillatorCell>("oscillator")

    val OSCILLATOR_PART = part("oscillator", BasicPartProvider( { a, b ->
        OscillatorPart(a, b)
    }, Vec3(1.0, 1.0, 1.0)))

    val LIGHT_PART = part("light_part", BasicPartProvider({ a, b -> LightPart(a, b, LIGHT_CELL.get()) }, bbVec(8.0, 4.0, 5.0)))

    val TEST_BULB = item("light_bulb") {
        LightBulbItem(LightModel(
            temperatureFunction = {
                it.power / 100.0
            },
            resistanceFunction = {
                Quantity(1.0, OHM)
            },
            damageFunction = { v, dt ->
                //dt * 0.01 * (v.power / 100.0)
                0.0
            },
            volumeProvider = let {
                val result: LightVolumeProvider

                val time = measureDuration {
                    result = LightFieldPrimitives.cone(32, 24.0, Math.toRadians(45.0))
                }

                LOG.info("BUILD TIME: ${(time .. MILLISECONDS).formatted()} ms")

                result
            }
        ))
    }

    fun clientWork() {
        MenuScreens.register(HEAT_GENERATOR_MENU.get(), ::HeatGeneratorScreen)
        LOG.info("Content client work completed")
    }
}
