@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.KILOGRAM
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.WATT_PER_KELVIN
import org.ageseries.libage.data.WATT_PER_METER_KELVIN
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMassDefinition
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.ThermalTint
import org.eln2.mc.common.LightFieldPrimitives
import org.eln2.mc.common.cells.CellRegistry.cellMemoize
import org.eln2.mc.common.cells.foundation.CellFactory
import org.eln2.mc.common.cells.foundation.ElectricalSize
import org.eln2.mc.common.cells.foundation.RadiantBodyEmissionDescription
import org.eln2.mc.common.cells.foundation.ThermalSize
import org.eln2.mc.common.content.ElectricalWireBuilder
import org.eln2.mc.common.content.RadiatorPart
import org.eln2.mc.common.content.ThermalWireBuilder
import org.eln2.mc.common.content.ThermalWireCell
import org.eln2.mc.common.content.WireRenderModel
import org.eln2.mc.common.content.WireThermalProperties
import org.eln2.mc.common.parts.PartRegistry.partImmediateBB

object Eln2Wires : ContentModule() {
    val UNINSULATED_WIRE_LIGHT_FIELD = LightFieldPrimitives.sourceOnlyStart(15)

    val STANDARD_UNINSULATED_COPPER_THERMAL_WIRE = ThermalWireBuilder("standard_uninsulated_copper_thermal_wire").applyAndRegister {
        temperatureThreshold = Quantity(800.0, CELSIUS)

        material = ThermalMassDefinition(
            ChemicalElement.Copper.asMaterial.copy(
                label = "Copper Thermal Conductor",
                thermalConductivity = Quantity(3500.0, WATT_PER_METER_KELVIN),
            )
        )

        leakageParameters = ConnectionParameters.DEFAULT.copy(
            conductance = Quantity(0.05, WATT_PER_KELVIN)
        )

        radiantDescription = RadiantBodyEmissionDescription(
            UNINSULATED_WIRE_LIGHT_FIELD
        )

        renderer {
            WireRenderModel(
                FlwModels.UNINSULATED_THERMAL_WIRE_HUB,
                FlwModels.UNINSULATED_THERMAL_WIRE_CONNECTION,
                ThermalTint.DEFAULT
            )
        }
    }

    val STANDARD_INSULATED_COPPER_THERMAL_WIRE = ThermalWireBuilder("standard_insulated_copper_thermal_wire").applyAndRegister {
        isIncandescent = false

        temperatureThreshold = Quantity(700.0, CELSIUS)

        material = ThermalMassDefinition(
            ChemicalElement.Copper.asMaterial.copy(
                label = "Copper Thermal Conductor",
                thermalConductivity = Quantity(3500.0, WATT_PER_METER_KELVIN),
            )
        )

        leakageParameters = ConnectionParameters.DEFAULT.copy(
            conductance = Quantity(0.01, WATT_PER_KELVIN) // Asbestos insulation
        )

        renderer {
            WireRenderModel(
                FlwModels.INSULATED_THERMAL_WIRE_HUB,
                FlwModels.INSULATED_THERMAL_WIRE_CONNECTION,
                ThermalTint.DEFAULT
            )
        }
    }

    val STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE = ElectricalWireBuilder("standard_insulated_copper_electrical_wire").applyAndRegister {
        isIncandescent = false

        temperatureThreshold = Quantity(150.0, CELSIUS)

        material = ThermalMassDefinition(
            ChemicalElement.Copper.asMaterial.copy(
                label = "Copper Electrical Conductor",
                thermalConductivity = Quantity(3500.0, WATT_PER_METER_KELVIN),
            )
        )

        leakageParameters = ConnectionParameters.DEFAULT.copy(
            conductance = Quantity(0.01, WATT_PER_KELVIN) // Insulation
        )

        breakdownPotential = 800.0

        renderer {
            WireRenderModel(
                FlwModels.ELECTRICAL_WIRE_HUB,
                FlwModels.ELECTRICAL_WIRE_CONNECTION
            )
        }
    }

    val SIGNAL_WIRE = ElectricalWireBuilder("signal_wire").applyAndRegister {
        isIncandescent = false

        temperatureThreshold = Quantity(133.0, CELSIUS)
        material = ThermalMassDefinition(ChemicalElement.Copper.asMaterial)
        leakageParameters = ConnectionParameters.DEFAULT.copy(conductance = Quantity(0.001, WATT_PER_KELVIN))
        breakdownPotential = 100.0

        size = ElectricalSize.Signal
        hubSize = Vector3d(1.5, 0.625, 1.5) / 16.0
        connectionSize = Vector3d(0.6, 0.4, 7.25) / 16.0

        renderer {
            WireRenderModel(
                FlwModels.SIGNAL_WIRE_HUB,
                FlwModels.SIGNAL_WIRE_CONNECTION
            )
        }
    }

    val THERMAL_RADIATOR_CELL = cellMemoize("thermal_radiator") {
        val thermalProperties = WireThermalProperties(
            ThermalMassDefinition(
                ChemicalElement.Copper.asMaterial,
                mass = Quantity(50.0, KILOGRAM)
            ),
            Quantity(900.0, CELSIUS),
            replicatesInternalTemperature = true,
            replicatesExternalTemperature = true,
            null, // TODO maybe it does radiate?
            leakageParameters = ConnectionParameters(
                conductance = Quantity(25.0, WATT_PER_KELVIN),
                area = 5.0
            )
        )

        CellFactory {
            ThermalWireCell(
                it,
                Double.POSITIVE_INFINITY,
                ThermalSize.Any,
                thermalProperties
            )
        }
    }

    val THERMAL_RADIATOR_PART = partImmediateBB("thermal_radiator", 16.0, 3.0, 16.0, ::RadiatorPart)
}
