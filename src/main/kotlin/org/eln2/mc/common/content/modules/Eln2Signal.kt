@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import net.minecraft.client.gui.screens.MenuScreens
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.geometry.Vector4d
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.BasicPartVisual
import org.eln2.mc.client.render.foundation.FlwVisualizerRegistry.setPartVisualizer
import org.eln2.mc.common.cells.CellRegistry.cellImmediate
import org.eln2.mc.common.cells.CellRegistry.cellMemoize
import org.eln2.mc.common.cells.foundation.CellFactory
import org.eln2.mc.common.cells.foundation.ElectricalSize
import org.eln2.mc.common.containers.ContainerRegistry.menu
import org.eln2.mc.common.content.*
import org.eln2.mc.common.content.OscilloscopePart.OscilloscopeScreen
import org.eln2.mc.common.grids.GridMaterialCategory
import org.eln2.mc.common.parts.PartRegistry.partImmediateBB
import org.eln2.mc.common.parts.PartRegistry.partMemoizeBB
import org.eln2.mc.common.parts.foundation.PartFactory
import org.eln2.mc.common.parts.foundation.eln2ReadPartGuiData
import org.eln2.mc.directionPoleMapPlanar
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.monopolarMapPlanar
import org.eln2.mc.nullMonopoleMap

object Eln2Signal : ContentModule() {
    override fun registerPartVisualizers() {
        setPartVisualizer<OscilloscopePart>(FLAT_OSCILLOSCOPE_PART.part.get()) { ctx, part ->
            BasicPartVisual(
                ctx, part,
                FlwModels.FLAT_OSCILLOSCOPE_PART
            )
        }

        setPartVisualizer<OscilloscopePart>(BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_PART.part.get()) { ctx, part ->
            BasicPartVisual(
                ctx, part,
                FlwModels.BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_PART,
                smoothLighting = true
            )
        }
    }

    override fun setupScreens() {
        MenuScreens.register(FLAT_OSCILLOSCOPE_MENU.get(), ::OscilloscopeScreen)
    }

    //#region Probes

    // All probe models should be made like this, and all cells use these directions.
    val STANDARD_PROBE_PLUS_DIRECTION = Base6Direction3d.Front
    val STANDARD_PROBE_MINUS_DIRECTION = Base6Direction3d.Back
    val STANDARD_PROBE_OUTPUT_DIRECTION = Base6Direction3d.Right

    val STANDARD_PROBE_COMPARER_MAP = directionPoleMapPlanar(STANDARD_PROBE_PLUS_DIRECTION, STANDARD_PROBE_MINUS_DIRECTION)
    val STANDARD_PROBE_OUTPUT_MAP = monopolarMapPlanar(STANDARD_PROBE_OUTPUT_DIRECTION)

    val STANDARD_PROBE_MODELS = mapOf(
        STANDARD_PROBE_PLUS_DIRECTION to FlwModels.STANDARD_CONNECTION,
        STANDARD_PROBE_MINUS_DIRECTION to FlwModels.STANDARD_CONNECTION,
        STANDARD_PROBE_OUTPUT_DIRECTION to FlwModels.SIGNAL_WIRE_CONNECTION.hub
    )

    val POTENTIAL_PROBE_CELL = cellImmediate("potential_probe") {
        ElectricalProbeCell(
            it,
            STANDARD_PROBE_COMPARER_MAP,
            ElectricalSize.Standard,
            STANDARD_PROBE_OUTPUT_MAP,
            ElectricalProbeType.Potential
        )
    }

    val POTENTIAL_PROBE_PART = partImmediateBB("potential_probe", 6.0, 2.025, 9.5) {
        ElectricalProbePart(
            it,
            FlwModels.POTENTIAL_PROBE_BODY,
            STANDARD_PROBE_MODELS,
            POTENTIAL_PROBE_CELL.get()
        )
    }

    val CURRENT_PROBE_CELL = cellImmediate("current_probe") {
        ElectricalProbeCell(
            it,
            STANDARD_PROBE_COMPARER_MAP,
            ElectricalSize.Standard,
            STANDARD_PROBE_OUTPUT_MAP,
            ElectricalProbeType.Current
        )
    }

    val CURRENT_PROBE_PART = partImmediateBB("current_probe", 6.0, 2.025, 9.5) {
        ElectricalProbePart(
            it,
            FlwModels.CURRENT_PROBE_BODY,
            STANDARD_PROBE_MODELS,
            CURRENT_PROBE_CELL.get()
        )
    }

    //#endregion

    //#region Relays

    val RELAY_CELL = cellMemoize("relay") {
        val options = RelayOptions(
            Quantity(1e-5, OHM),
            Quantity(ElectricalSimulation.MAX_RESISTANCE, OHM),
            ThermalMassDefinition(
                ChemicalElement.Iron.asMaterial,
                mass = Quantity(0.5, KILOGRAM)
            ),
            Quantity(200.0, CELSIUS),
            Quantity(1000.0, VOLT),
            maxSwitchingInterval = 40
        )

        val leakage = ConnectionParameters.DEFAULT

        CellFactory {
            RelayCell(it, STANDARD_PROBE_COMPARER_MAP, ElectricalSize.Standard, STANDARD_PROBE_OUTPUT_MAP, options, leakage)
        }
    }

    val RELAY_PART = partImmediateBB("relay", 6.0, 2.025, 9.5) {
        RelayPart(
            it,
            FlwModels.RELAY_BODY,
            FlwModels.RELAY_CONTACT,
            Vector3d(0.0, -1.0 / 16.0, 0.0),
            STANDARD_PROBE_MODELS,
            RELAY_CELL.get()
        )
    }

    //#endregion

    //#region Signal OpAmp

    val SIGNAL_OPAMP_INPUT_A_MAP = monopolarMapPlanar(Base6Direction3d.Left)
    val SIGNAL_OPAMP_INPUT_B_MAP = monopolarMapPlanar(Base6Direction3d.Right)
    val SIGNAL_OPAMP_OUTPUT_MAP = monopolarMapPlanar(Base6Direction3d.Front)

    val SIGNAL_OPAMP_MODELS = mapOf(
        Base6Direction3d.Left to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Right to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Front to FlwModels.SIGNAL_WIRE_CONNECTION.hub
    )

    val SIGNAL_OPAMP_CELL = cellImmediate("signal_opamp") {
        SignalOpAmpCell(it, SIGNAL_OPAMP_INPUT_A_MAP, SIGNAL_OPAMP_INPUT_B_MAP, SIGNAL_OPAMP_OUTPUT_MAP)
    }

    val SIGNAL_OPAMP_PART = partImmediateBB("signal_opamp", 7.3, 1.25, 7.3) {
        SignalOpAmpPart(it, SIGNAL_OPAMP_CELL.get())
    }

    //#region

    //#region Signal Reference

    val SIGNAL_REFERENCE_MODELS = mapOf(
        Base6Direction3d.Left to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Right to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Front to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Back to FlwModels.SIGNAL_WIRE_CONNECTION.hub
    )

    val SIGNAL_REFERENCE_OUTPUT_MAP = monopolarMapPlanar(Base6Direction3dMask.HORIZONTALS)

    val SIGNAL_REFERENCE_CELL = cellImmediate("signal_reference") {
        SignalReferenceCell(it, SIGNAL_REFERENCE_OUTPUT_MAP)
    }

    val SIGNAL_REFERENCE_PART = partImmediateBB("signal_reference", 4.0, 2.0, 4.0) {
        SignalReferencePart(it, SIGNAL_REFERENCE_CELL.get())
    }

    //#endregion

    //#region Signal Clamper

    val SIGNAL_CLAMPER_INPUT_MAP = monopolarMapPlanar(Base6Direction3d.Front)
    val SIGNAL_CLAMPER_OUTPUT_MAP = monopolarMapPlanar(Base6Direction3d.Back)

    val SIGNAL_CLAMPER_MODELS = mapOf(
        Base6Direction3d.Front to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Back to FlwModels.SIGNAL_WIRE_CONNECTION.hub
    )

    val SIGNAL_CLAMPER_CELL = cellImmediate("signal_clamper") {
        SignalClamperCell(it, SIGNAL_CLAMPER_INPUT_MAP, SIGNAL_CLAMPER_OUTPUT_MAP)
    }

    val SIGNAL_CLAMPER_PART = partImmediateBB("signal_clamper", 4.675, 1.25, 8.625) {
        SignalClamperPart(it, SIGNAL_CLAMPER_CELL.get())
    }

    //#endregion

    //#region Signal PID

    val SIGNAL_PID_INPUT_MAP = monopolarMapPlanar(Base6Direction3d.Left)
    val SIGNAL_PID_REFERENCE_MAP = monopolarMapPlanar(Base6Direction3d.Right)
    val SIGNAL_PID_OUTPUT_MAP = monopolarMapPlanar(Base6Direction3d.Front)

    val SIGNAL_PID_MODELS = mapOf(
        Base6Direction3d.Left to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Right to FlwModels.SIGNAL_WIRE_CONNECTION.hub,
        Base6Direction3d.Front to FlwModels.SIGNAL_WIRE_CONNECTION.hub
    )

    val SIGNAL_PID_CELL = cellImmediate("signal_pid") {
        SignalPidCell(
            it,
            SIGNAL_PID_INPUT_MAP,
            SIGNAL_PID_REFERENCE_MAP,
            SIGNAL_PID_OUTPUT_MAP
        )
    }

    val SIGNAL_PID_PART = partImmediateBB("signal_pid", 7.3, 1.325, 7.3) {
        SignalPidPart(it, SIGNAL_PID_CELL.get())
    }

    //#endregion

    //#region Oscilloscopes

    val BASIC_TWO_CHANNEL_OSCILLOSCOPE_SPECIFICATION = OscilloscopeSpecification(
        2,
        10,
        1000,
        OscilloscopePalette.DEFAULT,
        0.02f,
        0.0075f,
        Vector4d(0.8, 0.8, 1.0, 0.2),
        11,
        0.5f
    )

    val BASIC_TWO_CHANNEL_OSCILLOSCOPE_CELL = cellImmediate("basic_two_channel_oscilloscope") {
        OscilloscopeCell(it, nullMonopoleMap(), BASIC_TWO_CHANNEL_OSCILLOSCOPE_SPECIFICATION)
    }

    val FLAT_OSCILLOSCOPE_MENU = menu("basic_two_channel_oscilloscope_menu") { i, inv, buf ->
        OscilloscopePart.OscilloscopeMenu(i, buf.eln2ReadPartGuiData<OscilloscopePart>(inv))
    }

    val FLAT_OSCILLOSCOPE_PART = partMemoizeBB("basic_two_channel_oscilloscope", 15.2, 0.75, 10.0) {
        val generators = OscilloscopeChannelGenerators.create {
            withGridTerminal {
                defineCellBoxTerminalBB(
                    0.375, 0.1, 6.0,
                    0.625, 0.5, 0.5,
                    highlightColor = specification.palette.colorsInt[0],
                    categories = listOf(GridMaterialCategory.SignalGrid)
                )
            }

            withGridTerminal {
                defineCellBoxTerminalBB(
                    0.375, 0.1, 9.475,
                    0.625, 0.5, 0.5,
                    highlightColor = specification.palette.colorsInt[1],
                    categories = listOf(GridMaterialCategory.SignalGrid)
                )
            }
        }

        PartFactory {
            OscilloscopePart(it, BASIC_TWO_CHANNEL_OSCILLOSCOPE_SPECIFICATION, generators, BASIC_TWO_CHANNEL_OSCILLOSCOPE_CELL.get())
        }
    }

    val BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_SPECIFICATION = OscilloscopeSpecification(
        1,
        10,
        4096,
        OscilloscopePalette.DEFAULT,
        0.02f,
        0.0075f,
        Vector4d(0.8, 0.8, 1.0, 0.2),
        11,
        0.5f
    )

    val BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_CELL = cellImmediate("basic_single_channel_oscilloscope") {
        OscilloscopeCell(it, monopolarMapPlanar(Base6Direction3d.Left), BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_SPECIFICATION)
    }

    val BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_PART = partMemoizeBB("basic_single_channel_oscilloscope", 15.2, 0.75, 10.0) {
        val generators = OscilloscopeChannelGenerators.create {
            withGridTerminal {
                defineCellBoxTerminalBB(
                    0.0, 0.0, 7.75,
                    1.0, 0.4, 0.5,
                    highlightColor = specification.palette.colorsInt[0],
                    categories = listOf(GridMaterialCategory.SignalGrid)
                )
            }
        }

        PartFactory {
            OscilloscopePart(it, BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_SPECIFICATION, generators, BASIC_SINGLE_CHANNEL_OSCILLOSCOPE_CELL.get())
        }
    }

    //#endregion
}
