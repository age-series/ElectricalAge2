@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import dev.engine_room.flywheel.api.visualization.VisualizerRegistry
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import net.minecraftforge.client.event.EntityRenderersEvent
import org.ageseries.libage.data.FARAD
import org.ageseries.libage.data.KILOGRAM
import org.ageseries.libage.data.OHM
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.VOLT
import org.ageseries.libage.data.WATT
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMassDefinition
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.monopolarMapPlanar
import org.eln2.mc.client.render.foundation.BasicSpecVisual
import org.eln2.mc.client.render.foundation.DummyBlockEntityRendererProvider
import org.eln2.mc.client.render.foundation.FlwVisualizerRegistry.setSpecVisualizer
import org.eln2.mc.client.render.foundation.TestBlockEntityVisual
import org.eln2.mc.common.blocks.BlockRegistry.blockAndItem
import org.eln2.mc.common.blocks.BlockRegistry.blockEntityOnly
import org.eln2.mc.common.cells.CellRegistry.cellMemoize
import org.eln2.mc.common.cells.foundation.CellFactory
import org.eln2.mc.common.content.DcToDcConverterModel
import org.eln2.mc.common.content.DcToDcConverterSpec
import org.eln2.mc.common.content.PrimitivePowerConverterBlock
import org.eln2.mc.common.content.PrimitivePowerConverterBlockEntity
import org.eln2.mc.common.content.TerminalDcToDcConverterCell
import org.eln2.mc.common.content.modules.ContentManager.withSelfDrop
import org.eln2.mc.common.sounds.SoundRegistry.soundEventVariableRange
import org.eln2.mc.extensions.transformFacingBlock
import org.eln2.mc.common.specs.SpecRegistry.specImmediateBB

object Eln2PowerDevices : ContentModule() {
    override fun registerSpecVisualizers() {
        setSpecVisualizer<DcToDcConverterSpec>(Eln2PowerDevices.DC_TO_DC_CONVERTER_SPEC.spec.get()) { ctx, spec ->
            BasicSpecVisual(
                ctx, spec,
                FlwModels.SMALL_DC_TO_DC_CONVERTER
            )
        }
    }

    override fun registerBlockEntityVisualizers() {
        VisualizerRegistry.setVisualizer(
            PRIMITIVE_DC_TO_DC_CONVERTER_BLOCK_ENTITY.get(),
            SimpleBlockEntityVisualizer({ ctx, blockEntity, partialTick ->
                TestBlockEntityVisual(ctx, blockEntity, partialTick, FlwModels.PRIMITIVE_DC_TO_DC_CONVERTER) { instance, renderer ->
                    instance.transformFacingBlock(renderer.visualPosition, blockEntity)
                }
            }) { true }
        )
    }

    override fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(
            PRIMITIVE_DC_TO_DC_CONVERTER_BLOCK_ENTITY.get(),
            DummyBlockEntityRendererProvider()
        )
    }

    val TERMINAL_DC_TO_DC_CONVERTER_CELL_800W = cellMemoize("terminal_dc_to_dc_converter_800w") {
        val thermalDef = ThermalMassDefinition(
            ChemicalElement.Iron.asMaterial,
            mass = Quantity(1.0, KILOGRAM)
        )

        val environmentParameters = ConnectionParameters(
            conductance = Quantity(2.5)
        )

        val model = DcToDcConverterModel(
            Quantity(800.0, WATT),
            0.8,
            2.5,
            Quantity(800.0, VOLT),
            Quantity(5.0, VOLT),
            Quantity(0.0006571, OHM),
            Quantity(0.1027, FARAD),
            Quantity(0.0001167, OHM),
            Quantity(0.000891, OHM)
        )

        CellFactory {
            TerminalDcToDcConverterCell(it, thermalDef, environmentParameters, model)
        }
    }

    val DC_TO_DC_CONVERTER_SPEC = specImmediateBB(
        "micro_grid_dc_to_dc_converter_800w",
        FlwModels.SMALL_DC_TO_DC_CONVERTER,
        9.8, 2.625, 4.85,
        ::DcToDcConverterSpec
    )

    val PRIMITIVE_DC_TO_DC_CONVERTER_CELL_5KW = cellMemoize("primitive_dc_to_dc_converter_5kw") {
        val thermalDef = ThermalMassDefinition(
            ChemicalElement.Iron.asMaterial,
            mass = Quantity(8.0, KILOGRAM)
        )

        val environmentParameters = ConnectionParameters(
            conductance = Quantity(0.25)
        )

        val model = DcToDcConverterModel(
            Quantity(5000.0, WATT),
            0.7,
            1.5,
            Quantity(300.0, VOLT),
            Quantity(5.0, VOLT),
            Quantity(0.0075, OHM),
            Quantity(0.25, FARAD),
            Quantity(0.01, OHM),
            Quantity(0.0075, OHM)
        )

        val signalMap = monopolarMapPlanar(Base6Direction3dMask.HORIZONTALS)

        CellFactory {
            TerminalDcToDcConverterCell(it, thermalDef, environmentParameters, model, signalMap)
        }
    }

    val PRIMITIVE_DC_TO_DC_CONVERTER_BLOCK = blockAndItem("primitive_dc_to_dc_converter", ::PrimitivePowerConverterBlock)
        .withSelfDrop()

    val PRIMITIVE_DC_TO_DC_CONVERTER_BLOCK_ENTITY = blockEntityOnly(
        "primitive_dc_to_dc_converter",
        PRIMITIVE_DC_TO_DC_CONVERTER_BLOCK.block,
        ::PrimitivePowerConverterBlockEntity
    )

    val POWER_ELECTROMAGNETIC_SOUND = soundEventVariableRange("power.electromagnetic")
}
