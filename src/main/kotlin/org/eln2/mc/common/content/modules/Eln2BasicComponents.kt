@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import org.ageseries.libage.data.*
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.BasicPartVisual
import org.eln2.mc.client.render.foundation.BasicSpecVisual
import org.eln2.mc.client.render.foundation.FlwVisualizerRegistry.setPartVisualizer
import org.eln2.mc.client.render.foundation.FlwVisualizerRegistry.setSpecVisualizer
import org.eln2.mc.common.cells.CellRegistry.cellImmediate
import org.eln2.mc.common.cells.CellRegistry.cellMemoize
import org.eln2.mc.common.cells.foundation.CellFactory
import org.eln2.mc.common.cells.foundation.ElectricalSize
import org.eln2.mc.common.content.*
import org.eln2.mc.common.items.ItemRegistry.item
import org.eln2.mc.common.parts.PartRegistry.partImmediateBB
import org.eln2.mc.common.sounds.SoundRegistry.soundEventVariableRange
import org.eln2.mc.common.specs.SpecRegistry.specImmediateBB
import org.eln2.mc.directionPoleMapPlanar
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.monopolarMapPlanar

object Eln2BasicComponents : ContentModule() {
    override fun registerPartVisualizers() {
        setPartVisualizer<DiodePart>(DIODE_PART.part.get()) { ctx, part ->
            BasicPartVisual(
                ctx, part,
                FlwModels.DIODE,
                smoothLighting = true
            )
        }

        setPartVisualizer<SwitchPart>(SWITCH_PART.part.get()) { ctx, part ->
            SwitchPartVisual(ctx, part)
        }

        setPartVisualizer<FusePanelPart>(FUSE_PANEL_PART.part.get()) { ctx, part ->
            FusePanelPartVisual(ctx, part)
        }
    }

    override fun registerSpecVisualizers() {
        setSpecVisualizer<GroundSpec>(GROUND_SPEC.spec.get()) { ctx, spec ->
            BasicSpecVisual(
                ctx, spec,
                FlwModels.GROUND_MICRO_GRID
            )
        }
    }

    val VOLTAGE_SOURCE_CELL = cellMemoize("voltage_source") {
        val map = monopolarMapPlanar(Base6Direction3d.Front)
        val size = ElectricalSize.Standard

        CellFactory {
            VoltageSourceCell(it, map, size)
        }
    }

    val VOLTAGE_SOURCE_PART = partImmediateBB("voltage_source", 6.0, 2.5, 6.0, ::VoltageSourcePart)

    val GROUND_CELL = cellImmediate("ground", ::GroundCell)

    val GROUND_PART = partImmediateBB("ground", 4.0, 4.0, 4.0, ::GroundPart)

    val GROUND_SPEC = specImmediateBB(
        "ground_micro_grid",
        FlwModels.GROUND_MICRO_GRID,
        2.0, 2.0, 2.0,
        ::GroundSpec
    )

    val DIODE_CELL = cellMemoize("diode") {
        val poleMap = directionPoleMapPlanar(
            Base6Direction3d.Back,
            Base6Direction3d.Front
        )

        val model = DiodeOptions(
            Quantity(0.001, OHM),
            Quantity(10.0, KILO * OHM),
            ThermalMassDefinition(
                ChemicalElement.Iron.asMaterial,
                mass = Quantity(1.691, KILOGRAM)
            ),
            Quantity(148.61, CELSIUS),
            Quantity(800.0, VOLT)
        )

        val leakage = ConnectionParameters.DEFAULT

        CellFactory {
            DiodeCell(it, poleMap, model, leakage)
        }
    }

    val DIODE_PART = partImmediateBB("diode", 3.0, 2.275, 16.0) {
        DiodePart(it)
    }

    val SWITCH_SOUND = soundEventVariableRange("switch.scrape")

    val SWITCH_CELL = cellMemoize("switch") {
        val poleMap = directionPoleMapPlanar(
            Base6Direction3d.Back,
            Base6Direction3d.Front
        )

        val model = SwitchOptions(
            Quantity(1e-5, OHM),
            Quantity(ElectricalSimulation.MAX_RESISTANCE, OHM),
            ThermalMassDefinition(
                ChemicalElement.Iron.asMaterial,
                mass = Quantity(0.5, KILOGRAM)
            ),
            Quantity(200.0, CELSIUS),
            Quantity(1000.0, VOLT)
        )

        val leakage = ConnectionParameters.DEFAULT

        CellFactory {
            SwitchCell(it, poleMap, model, leakage)
        }
    }

    val SWITCH_PART = partImmediateBB("switch", 6.0, 4.0, 16.0) {
        SwitchPart(it)
    }

    val BURNT_FUSE_ITEM = item("burnt_fuse", ::BurntFuseItem)

    val TIN_FUSE_1A = item("tin_fuse_1a") {
        FuseItem(FuseModel(
            materialName = "Tin",
            ratedCurrent = Quantity(1.0, AMPERE),
            resistance = Quantity(0.5, OHM),
            massDef = ThermalMassDefinition(
                ChemicalElement.Tin.asMaterial,
                mass = Quantity(0.0001, KILOGRAM)
            ),
            meltingPoint = Quantity(232.0, CELSIUS),
            leakage = ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(0.003, WATT_PER_KELVIN)
            )
        ))
    }

    val TIN_FUSE_5A = item("tin_fuse_5a") {
        FuseItem(FuseModel(
            materialName = "Tin",
            ratedCurrent = Quantity(5.0, AMPERE),
            resistance = Quantity(0.06, OHM),
            massDef = ThermalMassDefinition(
                ChemicalElement.Tin.asMaterial,
                mass = Quantity(0.001, KILOGRAM)
            ),
            meltingPoint = Quantity(232.0, CELSIUS),
            leakage = ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(0.01, WATT_PER_KELVIN)
            )
        ))
    }

    val LEAD_FUSE_10A = item("lead_fuse_10a") {
        FuseItem(FuseModel(
            materialName = "Lead",
            ratedCurrent = Quantity(10.0, AMPERE),
            resistance = Quantity(0.02, OHM),
            massDef = ThermalMassDefinition(
                ChemicalElement.Lead.asMaterial,
                mass = Quantity(0.005, KILOGRAM)
            ),
            meltingPoint = Quantity(327.6, CELSIUS),
            leakage = ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(0.01, WATT_PER_KELVIN)
            )
        ))
    }

    val LEAD_FUSE_25A = item("lead_fuse_25a") {
        FuseItem(FuseModel(
            materialName = "Lead",
            ratedCurrent = Quantity(25.0, AMPERE),
            resistance = Quantity(0.003, OHM),
            massDef = ThermalMassDefinition(
                ChemicalElement.Lead.asMaterial,
                mass = Quantity(0.02, KILOGRAM)
            ),
            meltingPoint = Quantity(327.6, CELSIUS),
            leakage = ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(0.01, WATT_PER_KELVIN)
            )
        ))
    }

    val BRONZE_FUSE_50A = item("bronze_fuse_50a") {
        FuseItem(FuseModel(
            materialName = "Bronze",
            ratedCurrent = Quantity(50.0, AMPERE),
            resistance = Quantity(0.002, OHM),
            massDef = ThermalMassDefinition(
                Material(
                    "Bronze",
                    Quantity(1.5e-7, OHM_METER),
                    Quantity(50.0, WATT_PER_METER_KELVIN),
                    Quantity(370.0, JOULE_PER_KILOGRAM_KELVIN),
                    Quantity(8800.0, KILOGRAM_PER_METER3)
                ),
                mass = Quantity(0.02, KILOGRAM)
            ),
            meltingPoint = Quantity(770.0, CELSIUS),
            leakage = ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(0.01, WATT_PER_KELVIN)
            )
        ))
    }

    val COPPER_FUSE_100A = item("copper_fuse_100a") {
        FuseItem(FuseModel(
            materialName = "Copper",
            ratedCurrent = Quantity(100.0, AMPERE),
            resistance = Quantity(0.0005, OHM),
            massDef = ThermalMassDefinition(
                ChemicalElement.Copper.asMaterial,
                mass = Quantity(0.05, KILOGRAM)
            ),
            meltingPoint = Quantity(1084.6, CELSIUS),
            leakage = ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(0.005, WATT_PER_KELVIN)
            )
        ))
    }

    val COPPER_FUSE_150A = item("copper_fuse_150a") {
        FuseItem(FuseModel(
            materialName = "Copper",
            ratedCurrent = Quantity(150.0, AMPERE),
            resistance = Quantity(0.0002, OHM),
            massDef = ThermalMassDefinition(
                ChemicalElement.Copper.asMaterial,
                mass = Quantity(0.08, KILOGRAM)
            ),
            meltingPoint = Quantity(1084.6, CELSIUS),
            leakage = ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(0.005, WATT_PER_KELVIN)
            )
        ))
    }

    val FUSE_CELL = cellMemoize("fuse") {
        val map = directionPoleMapPlanar(
            Base6Direction3d.Back,
            Base6Direction3d.Front
        )

        CellFactory {
            FusePanelCell(it, map)
        }
    }

    val FUSE_PANEL_PART = partImmediateBB("fuse_panel", 8.0, 4.0, 16.0) {
        FusePanelPart(it)
    }
}
