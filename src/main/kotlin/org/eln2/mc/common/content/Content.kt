@file:Suppress("MemberVisibilityCanBePrivate", "PublicApiImplicitType", "PublicApiImplicitType", "unused", "LongLine")

package org.eln2.mc.common.content

import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.Quantity
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
import org.eln2.mc.mathematics.*

/**
 * Joint registry for content classes.
 */
object Content {
    /**
     * Initializes the fields, in order to register the content.
     */
    fun initialize() {}

    val WRENCH = item("wrench") {
        WrenchItem()
    }

    //#region Wires

    val COPPER_THERMAL_WIRE = ThermalWireBuilder("thermal_wire_copper")
        .apply {
            damageOptions = TemperatureExplosionBehaviorOptions(temperatureThreshold = Quantity(1000.0, CELSIUS))
        }
        .register()

    val ELECTRICAL_WIRE_COPPER = ElectricalWireBuilder("electrical_cable_copper")
        .apply {
            isIncandescent = false
        }
        .register()

    val VOLTAGE_SOURCE_CELL = cell("voltage_source_cell", BasicCellProvider(::VoltageSourceCell))
    val VOLTAGE_SOURCE_PART = part("voltage_source_part", BasicPartProvider(::VoltageSourcePart, Vector3d(6.0 / 16.0, 2.5 / 16.0, 6.0 / 16.0)))

    val GROUND_CELL = cell("ground_cell", BasicCellProvider(::GroundCell))
    val GROUND_PART = part("ground_part", BasicPartProvider(::GroundPart, Vector3d(4.0 / 16.0, 4.0 / 16.0, 4.0 / 16.0)))

    val BATTERY_CELL_100V = cell("battery_cell_t", BasicCellProvider { createInfo ->
        BatteryCell(
            createInfo, BatteryModels.LEAD_ACID_12V
        ).also { it.energy = it.model.energyCapacity * 0.9 }
    })

    val BATTERY_PART_100V =
        part(
            "battery_part_100v",
            BasicPartProvider({ a, b ->
                BatteryPart(a, b, BATTERY_CELL_100V.get()) { part ->
                    BasicPartRenderer(part, PartialModels.BATTERY)
                }
            }, Vector3d(6.0 / 16.0, 7.0 / 16.0, 10.0 / 16.0))
        )

    val RESISTOR_CELL = cell("resistor_cell", BasicCellProvider(::ResistorCell))
    val RESISTOR_PART = part("resistor_part", BasicPartProvider(::ResistorPart, Vector3d(3.5 / 16.0, 2.25 / 16.0, 5.0 / 16.0)))

    val THERMAL_RADIATOR_CELL = injCell<ThermalWireCell>(
        "thermal_radiator_cell",
        WireThermalProperties(
            ThermalBodyDef(Material.COPPER, 10.0, 50.0),
            TemperatureExplosionBehaviorOptions(temperatureThreshold = Quantity(1000.0, CELSIUS)),
            true,
            true
        )
    )
    val THERMAL_RADIATOR = part("thermal_radiator_part", BasicPartProvider({ id, ctx ->
        RadiatorPart(id, ctx, defaultRadiantBodyColor()) }, Vector3d(1.0, 3.0 / 16.0, 1.0)))

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
    val ELECTRICAL_ENERGY_METER_PART = part("electrical_energy_meter_part", BasicPartProvider(::ElectricalEnergyMeterPart, Vector3d(1.0, 1.0, 1.0)))


    val PHOTOVOLTAIC_GENERATOR_CELL = cell("photovoltaic_cell", BasicCellProvider {
        PhotovoltaicGeneratorCell(it, PhotovoltaicModels.test24Volts())
    })

    val PHOTOVOLTAIC_PANEL_PART = part("photovoltaic_panel_part", BasicPartProvider({ id, context ->
        BasicCellPart(
            id,
            context,
            PHOTOVOLTAIC_GENERATOR_CELL.get(),
            basicPartRenderer(
                PartialModels.SOLAR_PANEL_ONE_BLOCK,
            )
        )
    }, Vector3d(1.0, 2.0 / 16.0, 1.0)))

    val LIGHT_CELL = cell("light_cell", BasicCellProvider { context ->
        LightCell(context, directionPoleMapPlanar(Base6Direction3d.Left, Base6Direction3d.Right)).also { cell ->
            cell.ruleSet.withDirectionRulePlanar(Base6Direction3dMask.LEFT + Base6Direction3dMask.RIGHT)
        }
    })

    val LIGHT_PART = part("light_part", BasicPartProvider({ a, b -> LightPart(a, b, LIGHT_CELL.get()) }, Vector3d(8.0 / 16.0, (1.0 + 2.302) / 16.0, 5.0 / 16.0)))

    val OSCILLATOR_CELL = injCell<OscillatorCell>("oscillator")

    val OSCILLATOR_PART = part("oscillator", BasicPartProvider( { a, b ->
        OscillatorPart(a, b)
    }, Vector3d(1.0, 1.0, 1.0)))

    val TEST_BULB_1 = item("light_bulb_1") {
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
            volumeProvider = LightFieldPrimitives.cone(32, 48.0, Math.toRadians(45.0), 1)
        ))
    }

    val GRID_CELL = injCell<GridCell>("grid_cell")
    val GRID_TAP_PART = part("grid_tap", BasicPartProvider( { a, b ->
        GridTapPart(a, b, GRID_CELL.get())
    }, Vector3d(4.0 / 16.0, 0.5, 4.0 / 16.0)))
    val GRID_CONNECT_COPPER = item("grid_connect_copper") { GridConnectItem(GridMaterials.COPPER_AS_COPPER_COPPER) }
    val GRID_CONNECT_STEEL = item("grid_connect_steel") { GridConnectItem(GridMaterials.NEUTRAL_AS_STEEL_IRON) }
    val GRID_CONNECT_RUBBER = item("grid_connect_rubber") { GridConnectItem(GridMaterials.NEUTRAL_AS_RUBBER_COPPER) }

    fun clientWork() {
        MenuScreens.register(HEAT_GENERATOR_MENU.get(), ::HeatGeneratorScreen)
        LOG.info("Content client work completed")
    }
}
