@file:Suppress("MemberVisibilityCanBePrivate", "PublicApiImplicitType", "PublicApiImplicitType", "unused", "LongLine")

package org.eln2.mc.common.content

import net.minecraft.client.gui.screens.MenuScreens
import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.M2
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.sim.Material
import org.eln2.mc.LOG
import org.eln2.mc.ThermalBodyDef
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.cutout
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.client.render.foundation.defaultRadiantBodyColor
import org.eln2.mc.client.render.solid
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
import org.eln2.mc.toVector3d
import kotlin.math.PI

/**
 * Joint registry for content classes.
 */
object Content {
    /**
     * Initializes the fields, in order to register the content.
     */
    fun initialize() {}

    val WRENCH = item("wrench") { WrenchItem() }

    //#region Wires

    val COPPER_THERMAL_WIRE = ThermalWireBuilder("thermal_wire_copper")
        .apply {
            damageOptions = TemperatureExplosionBehaviorOptions(
                temperatureThreshold = Quantity(1000.0, CELSIUS)
            )
        }
        .register()

    val ELECTRICAL_WIRE_COPPER = ElectricalWireBuilder("electrical_cable_copper")
        .apply {
            isIncandescent = false
        }
        .register()

    val VOLTAGE_SOURCE_CELL = cell(
        "voltage_source",
        BasicCellProvider(::VoltageSourceCell)
    )

    val VOLTAGE_SOURCE_PART = part(
        "voltage_source",
        BasicPartProvider(
            ::VoltageSourcePart,
            Vector3d(6.0 / 16.0, 2.5 / 16.0, 6.0 / 16.0)
        )
    )

    val GROUND_CELL = cell(
        "ground",
        BasicCellProvider(::GroundCell)
    )

    val GROUND_PART = part(
        "ground",
        BasicPartProvider(
            ::GroundPart,
            Vector3d(4.0 / 16.0, 4.0 / 16.0, 4.0 / 16.0)
        )
    )

    val BATTERY_CELL_12V = cell(
        "lead_acid_battery_12v",
        BasicCellProvider { ci ->
            BatteryCell(ci, BatteryModels.LEAD_ACID_12V).also {
                it.energy = it.model.energyCapacity * 0.9
            }
    })

    val BATTERY_PART_12V = part(
        "lead_acid_battery_12v",
        BasicPartProvider({ ci ->
            BatteryPart(ci, BATTERY_CELL_12V.get()) { part ->
                BasicPartRenderer(part, PartialModels.BATTERY)
            }
        }, Vector3d(6.0 / 16.0, 7.0 / 16.0, 10.0 / 16.0))
    )

    val RESISTOR_CELL = cell(
        "resistor",
        BasicCellProvider(::ResistorCell)
    )

    val RESISTOR_PART = part(
        "resistor",
        BasicPartProvider(
            ::ResistorPart,
            Vector3d(3.5 / 16.0, 2.25 / 16.0, 5.0 / 16.0)
        )
    )

    val THERMAL_RADIATOR_CELL = cell(
        "thermal_radiator",
        BasicCellProvider { ci ->
            ThermalWireCell(
                ci,
                Double.POSITIVE_INFINITY,
                WireThermalProperties(
                    ThermalBodyDef(
                        Material.COPPER,
                        10.0,
                        50.0
                    ),
                    TemperatureExplosionBehaviorOptions(
                        temperatureThreshold = Quantity(1000.0, CELSIUS)
                    ),
                    replicatesInternalTemperature = true,
                    replicatesExternalTemperature = true
                )
            )
        }
    )
    val THERMAL_RADIATOR_PART = part(
        "thermal_radiator",
        BasicPartProvider({ ci ->
            RadiatorPart(ci, defaultRadiantBodyColor())
        }, Vector3d(1.0, 3.0 / 16.0, 1.0))
    )

    val HEAT_GENERATOR_CELL = injCell<HeatGeneratorCell>(
        "heat_generator",
        ThermalBodyDef(
            Material.COPPER,
            10.0,
            6.0
        )
    )
    val HEAT_GENERATOR_BLOCK = block("heat_generator", tab = null) {
        HeatGeneratorBlock()
    }

    val HEAT_GENERATOR_BLOCK_ENTITY = blockEntity(
        "heat_generator",
        ::HeatGeneratorBlockEntity
    ) { HEAT_GENERATOR_BLOCK.block.get() }

    val HEAT_GENERATOR_MENU = menu(
        "heat_generator",
        ::HeatGeneratorMenu
    )

    val PHOTOVOLTAIC_GENERATOR_CELL = cell(
        "photovoltaic_generator",
        BasicCellProvider { ci ->
            PhotovoltaicGeneratorCell(
                ci,
                Quantity(1.0, M2),
                PhotovoltaicModel(
                    Quantity(32.0, VOLT),
                    7000.0,
                    0.1,
                    0.8,
                    1.0,
                )
            ) { it.locator.requireLocator<FaceLocator>().toVector3d() }
        }
    )

    val PHOTOVOLTAIC_PANEL_PART = part(
        "photovoltaic_panel",
        BasicPartProvider({ ci ->
            PhotovoltaicPanelPart(
                ci,
                PHOTOVOLTAIC_GENERATOR_CELL.get()
            )
        }, Vector3d(1.0, 2.0 / 16.0, 1.0))
    )

    val LIGHT_CELL = cell(
        "light",
        BasicCellProvider { ci ->
            LightCell(ci, directionPoleMapPlanar(Base6Direction3d.Left, Base6Direction3d.Right)).also { cell ->
                cell.ruleSet.withDirectionRulePlanar(Base6Direction3dMask.LEFT + Base6Direction3dMask.RIGHT)
            }
        }
    )

    val LIGHT_PART = part(
        "light_part",
        BasicPartProvider({ ci ->
            PoweredLightPart(ci, LIGHT_CELL.get())
        }, Vector3d(8.0 / 16.0, (1.0 + 2.302) / 16.0, 5.0 / 16.0))
    )

    val OSCILLATOR_CELL = injCell<OscillatorCell>("oscillator")

    val OSCILLATOR_PART = part("oscillator", BasicPartProvider( { ci ->
        OscillatorPart(ci)
    }, Vector3d(1.0, 1.0, 1.0)))

    val LIGHT_BULB_12V_100W = item("light_bulb_12v_100w") {
        LightBulbItem(
            LightModel(
                temperatureFunction = {
                    it.power / 100.0
                },
                resistanceFunction = {
                    Quantity(1.44, OHM)
                },
                damageFunction = { v, dt ->
                    dt * (v.power / 100.0) * 1e-6
                },
                volumeProvider = LightFieldPrimitives.cone(
                    32,
                    24.0,
                    PI / 4.0,
                    1
                )
            )
        )
    }

    val GRID_CELL = injCell<GridCell>("grid")

    val GRID_TAP_PART = part(
        "grid_tap",
        BasicPartProvider( { ci ->
            GridTapPart(ci, GRID_CELL.get())
        }, Vector3d(4.0 / 16.0, 0.5, 4.0 / 16.0))
    )

    val GRID_CONNECT_COPPER = item("grid_connect_copper") {
        GridConnectItem(GridMaterials.COPPER_AS_COPPER_COPPER)
    }

    private const val GARDEN_LIGHT_INITIAL_CHARGE = 0.5

    private fun gardenLightModel(strength: Double) = SolarLightModel(
        solarScan(Vector3d.unitY),
        dischargeRate = 1.0 / 12000.0 * 0.9,
        LightFieldPrimitives.sphere(1, strength)
    )

    private val SMALL_GARDEN_LIGHT_MODEL = gardenLightModel(3.0)

    val SMALL_GARDEN_LIGHT = part(
        "small_garden_light",
        BasicPartProvider( { ci ->
            SolarLightPart(ci,
                SMALL_GARDEN_LIGHT_MODEL,
                { it.placement.face.toVector3d() },
                {
                    BasicPartRenderer(
                        it,
                        PartialModels.SMALL_GARDEN_LIGHT
                    )
                },
                BasicPartRenderer::class.java
            ).also { it.energy = GARDEN_LIGHT_INITIAL_CHARGE }
        }, Vector3d(4.0 / 16.0, 6.0 / 16.0, 4.0 / 16.0))
    )

    private val TALL_GARDEN_LIGHT_MODEL = gardenLightModel(7.0)

    val TALL_GARDEN_LIGHT = part(
        "tall_garden_light",
        BasicPartProvider( { ci ->
            SolarLightPart(ci,
                SMALL_GARDEN_LIGHT_MODEL,
                { it.placement.face.toVector3d() },
                {
                    LightFixtureRenderer(
                        it,
                        PartialModels.TALL_GARDEN_LIGHT_CAGE.cutout(),
                        PartialModels.TALL_GARDEN_LIGHT_EMITTER.solid()
                    )
                },
                LightFixtureRenderer::class.java
            ).also { it.energy = GARDEN_LIGHT_INITIAL_CHARGE }
        }, Vector3d(3.0 / 16.0, 15.5 / 16.0, 3.0 / 16.0))
    )

    val HEAT_ENGINE_ELECTRICAL_CELL = cell(
        "heat_engine_electrical",
        BasicCellProvider { ci ->
            HeatEngineElectricalCell(
                ci,
                directionPoleMapPlanar(
                    plusDir = Base6Direction3d.Front,
                    minusDir = Base6Direction3d.Back
                ),
                directionPoleMapPlanar(
                    plusDir = Base6Direction3d.Left,
                    minusDir = Base6Direction3d.Right
                ),
                ThermalBodyDef(
                    Material.COPPER,
                    25.0,
                    2.0
                ),
                HeatEngineElectricalModel(
                    efficiency = 0.9,
                    power = 1000.0,
                    leakageRate = 1e-3,
                    desiredVoltage = 100.0
                )
            ).also {
                it.generator.ruleSet.withDirectionRulePlanar(Base6Direction3dMask.FRONT + Base6Direction3dMask.BACK)
                it.thermalBipole.ruleSet.withDirectionRulePlanar(Base6Direction3dMask.LEFT + Base6Direction3dMask.RIGHT)
            }
        }
    )

    val HEAT_ENGINE_ELECTRICAL_PART = part(
        "heat_engine_electrical",
        BasicPartProvider({
        HeatEngineElectricalPart(it)
    }, Vector3d(0.5, 15.0 / 16.0, 0.5)))

    fun clientWork() {
        MenuScreens.register(HEAT_GENERATOR_MENU.get(), ::HeatGeneratorScreen)
        LOG.info("Content client work completed")
    }
}
