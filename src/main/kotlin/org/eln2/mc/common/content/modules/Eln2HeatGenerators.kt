@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import dev.engine_room.flywheel.api.visualization.VisualizerRegistry
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraftforge.client.event.EntityRenderersEvent
import org.ageseries.libage.data.KILOGRAM
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.WATT_PER_KELVIN
import org.ageseries.libage.data.WATT_PER_METER_KELVIN
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMassDefinition
import org.eln2.mc.PIDGains
import org.eln2.mc.client.render.foundation.DummyBlockEntityRendererProvider
import org.eln2.mc.client.screens.BasicProgressScreen
import org.eln2.mc.common.blocks.BlockRegistry.blockAndItem
import org.eln2.mc.common.blocks.BlockRegistry.blockEntityOnly
import org.eln2.mc.common.blocks.BlockRegistry.blockItemOnly
import org.eln2.mc.common.blocks.BlockRegistry.blockOnly
import org.eln2.mc.common.blocks.BlockRegistry.defineDelegateMap
import org.eln2.mc.common.blocks.foundation.BigBlockItem
import org.eln2.mc.common.blocks.foundation.MultiblockDelegateBlock
import org.eln2.mc.common.cells.CellRegistry.cellMemoize
import org.eln2.mc.common.cells.foundation.CellFactory
import org.eln2.mc.common.containers.ContainerRegistry.menu
import org.eln2.mc.common.content.*
import org.eln2.mc.common.content.modules.ContentManager.withSelfDrop
import org.eln2.mc.common.sounds.SoundRegistry.soundEventVariableRange
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.monopolarMapPlanar
import org.eln2.mc.resource

object Eln2HeatGenerators : ContentModule() {
    val BURNER_DRAFT_SOUND = soundEventVariableRange("burner.draft")

    override fun registerBlockEntityVisualizers() {
        VisualizerRegistry.setVisualizer(
            PRIMITIVE_BURNER_BLOCK_ENTITY.get(),
            SimpleBlockEntityVisualizer(::PrimitiveBurnerBlockEntityVisual) { true }
        )

        VisualizerRegistry.setVisualizer(
            ADVANCED_COAL_BURNER_BLOCK_ENTITY.get(),
            SimpleBlockEntityVisualizer(::AdvancedCoalBurnerMainBlockEntityVisual) { true }
        )
    }

    override fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(
            PRIMITIVE_BURNER_BLOCK_ENTITY.get(),
            DummyBlockEntityRendererProvider()
        )

        event.registerBlockEntityRenderer(
            ADVANCED_COAL_BURNER_BLOCK_ENTITY.get(),
            DummyBlockEntityRendererProvider()
        )
    }

    override fun setupScreens() {
        MenuScreens.register(ADVANCED_COAL_BURNER_MENU.get()) { menu, inventory, title ->
            BasicProgressScreen(
                menu, inventory, title,
                resource("textures/gui/container/advanced_coal_burner/base.png"),
                resource("textures/gui/container/advanced_coal_burner/progress.png"),
                79.0f,
                96.0f
            )
        }
    }

    //#region Primitive Burner

    const val PRIMITIVE_BURNER_MAX_DRAFT = 0.05

    val PRIMITIVE_BURNER_CELL = cellMemoize("primitive_burner") {
        val options = BurnerCellOptions(
            BurnerDeviceDescription(
                Quantity(15.0, WATT_PER_METER_KELVIN),
                Quantity(171.6, WATT_PER_METER_KELVIN),
                Quantity(0.05, WATT_PER_KELVIN),
                Quantity(1.21, WATT_PER_METER_KELVIN)
            ),
            ThermalMassDefinition(
                ChemicalElement.Iron.asMaterial,
                mass = Quantity(14.019, KILOGRAM)
            ),
            ConnectionParameters(
                conductance = Quantity(9.1, WATT_PER_KELVIN)
            )
        )

        val map = monopolarMapPlanar(Base6Direction3d.Back)

        CellFactory {
            PrimitiveBurnerCell(it, options, map, PRIMITIVE_BURNER_MAX_DRAFT)
        }
    }

    val PRIMITIVE_BURNER_BLOCK = blockAndItem("primitive_burner", ::PrimitiveBurnerBlock)
        .withSelfDrop()

    val PRIMITIVE_BURNER_BLOCK_ENTITY = blockEntityOnly("primitive_burner", PRIMITIVE_BURNER_BLOCK.block, ::PrimitiveBurnerBlockEntity)

    //#endregion

    //#region Advanced Coal Burner

    const val ADVANCED_COAL_BURNER_MAX_DRAFT = 0.15

    val ADVANCED_COAL_BURNER_CELL = cellMemoize("advanced_coal_burner") {
        val options = BurnerCellOptions(
            BurnerDeviceDescription(
                Quantity(15.0, WATT_PER_METER_KELVIN),
                Quantity(171.6, WATT_PER_METER_KELVIN),
                Quantity(0.05, WATT_PER_KELVIN),
                Quantity(1.21, WATT_PER_METER_KELVIN)
            ),
            ThermalMassDefinition(
                ChemicalElement.Iron.asMaterial,
                mass = Quantity(30.0, KILOGRAM)
            ),
            ConnectionParameters(
                conductance = Quantity(9.1, WATT_PER_KELVIN)
            )
        )

        val map = monopolarMapPlanar(Base6Direction3dMask.HORIZONTALS)

        val pidGains = PIDGains(
            kP = 0.002,
            kI = 0.00001,
            kD = 0.0001
        )

        CellFactory {
            AdvancedBurnerCell(it, options, map, ADVANCED_COAL_BURNER_MAX_DRAFT, pidGains)
        }
    }

    val ADVANCED_COAL_BURNER_DELEGATE_MAP = defineDelegateMap("advanced_coal_burner") {
        delegateBlock(0, 1, 0)
    }

    val ADVANCED_COAL_BURNER_DELEGATE_BLOCK_ENTITY = blockEntityOnly(
        "advanced_coal_burner_delegate",
        ::AdvancedCoalBurnerDelegateBlockEntity
    ) {
        ADVANCED_COAL_BURNER_DELEGATE_MAP.value.getBlocksOfType<MultiblockDelegateBlock>()
    }

    val ADVANCED_COAL_BURNER_BLOCK = blockOnly("advanced_coal_burner", ::AdvancedCoalBurnerBlock)
        .withSelfDrop()

    val ADVANCED_COAL_BURNER_BLOCK_ENTITY = blockEntityOnly(
        "advanced_coal_burner",
        ADVANCED_COAL_BURNER_BLOCK,
        ::AdvancedCoalBurnerBlockEntity
    )

    val ADVANCED_COAL_BURNER_BLOCK_ITEM = blockItemOnly("advanced_coal_burner") {
        BigBlockItem(
            ADVANCED_COAL_BURNER_DELEGATE_MAP.value,
            ADVANCED_COAL_BURNER_BLOCK.get()
        )
    }

    val ADVANCED_COAL_BURNER_MENU = menu("advanced_coal_burner", ::AdvancedCoalBurnerMenu)

    //#endregion
}
