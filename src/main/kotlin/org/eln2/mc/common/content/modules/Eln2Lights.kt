@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import dev.engine_room.flywheel.api.visualization.VisualizerRegistry
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import net.minecraft.core.BlockPos
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.OHM
import org.ageseries.libage.data.Potential
import org.ageseries.libage.data.Power
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Resistance
import org.ageseries.libage.data.VOLT
import org.ageseries.libage.data.WATT
import org.ageseries.libage.mathematics.geometry.BoundingBox3d
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.BasicPartVisual
import org.eln2.mc.client.render.foundation.DummyBlockEntityRendererProvider
import org.eln2.mc.client.render.foundation.FlwVisualizerRegistry.setPartVisualizer
import org.eln2.mc.common.LightBulbItem
import org.eln2.mc.common.LightFieldPrimitives
import org.eln2.mc.common.LightModel
import org.eln2.mc.common.LightVariantType
import org.eln2.mc.common.blocks.BlockRegistry.blockEntityOnly
import org.eln2.mc.common.blocks.BlockRegistry.blockItemOnly
import org.eln2.mc.common.blocks.BlockRegistry.blockOnly
import org.eln2.mc.common.blocks.BlockRegistry.defineDelegateMap
import org.eln2.mc.common.blocks.foundation.BigBlockItem
import org.eln2.mc.common.cells.CellRegistry.cellImmediate
import org.eln2.mc.common.cells.foundation.ElectricalSize
import org.eln2.mc.common.content.LampPoleBlock
import org.eln2.mc.common.content.LampPoleBlockEntity
import org.eln2.mc.common.content.LampPoleBlockEntityVisual
import org.eln2.mc.common.content.LightFixturePartVisual
import org.eln2.mc.common.content.PolarLightCell
import org.eln2.mc.common.content.PolarPoweredLightPart
import org.eln2.mc.common.content.SolarLightModel
import org.eln2.mc.common.content.SolarLightPart
import org.eln2.mc.common.content.TerminalLightCell
import org.eln2.mc.common.content.TerminalPoweredLightPart
import org.eln2.mc.common.content.modules.ContentManager.withSelfDrop
import org.eln2.mc.common.content.solarScan
import org.eln2.mc.common.items.CreativeTabRegistry
import org.eln2.mc.common.items.ItemRegistry.item
import org.eln2.mc.common.parts.PartRegistry.partAndItemWithProvider
import org.eln2.mc.common.parts.PartRegistry.partImmediateBB
import org.eln2.mc.common.parts.PartRegistry.partMemoizeBB
import org.eln2.mc.common.parts.foundation.BasicPartProvider
import org.eln2.mc.common.parts.foundation.PartFactory
import org.eln2.mc.directionPoleMapPlanar
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.maskXY
import kotlin.math.PI
import kotlin.math.pow

object Eln2Lights : ContentModule() {
    override fun registerBlockEntityVisualizers() {
        VisualizerRegistry.setVisualizer(
            LAMP_POLE_BLOCK_ENTITY.get(),
            SimpleBlockEntityVisualizer(::LampPoleBlockEntityVisual) { true }
        )
    }

    override fun registerPartVisualizers() {
        setPartVisualizer<SolarLightPart>(SMALL_GARDEN_LIGHT.part.get()) { ctx, part ->
            BasicPartVisual(
                ctx,
                part,
                FlwModels.SMALL_GARDEN_LIGHT
            )
        }

        setPartVisualizer<SolarLightPart>(TALL_GARDEN_LIGHT.part.get()) { ctx, part ->
            LightFixturePartVisual(
                ctx, part,
                FlwModels.TALL_GARDEN_LIGHT_CAGE,
                FlwModels.TALL_GARDEN_LIGHT_EMITTER
            )
        }

        setPartVisualizer<PolarPoweredLightPart>(LIGHT_PART.part.get()) { ctx, part ->
            LightFixturePartVisual(
                ctx, part,
                FlwModels.SMALL_WALL_LAMP_CAGE,
                FlwModels.SMALL_WALL_LAMP_EMITTER
            )
        }

        setPartVisualizer<TerminalPoweredLightPart>(LIGHT_PART_MICRO_GRID.part.get()) { ctx, part ->
            LightFixturePartVisual(
                ctx, part,
                FlwModels.SMALL_WALL_LAMP_CAGE_MICRO_GRID,
                FlwModels.SMALL_WALL_LAMP_EMITTER
            )
        }
    }

    override fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(
            LAMP_POLE_BLOCK_ENTITY.get(),
            DummyBlockEntityRendererProvider()
        )
    }

    val POLAR_LIGHT_CELL_CONE_45DEG = cellImmediate("polar_light_45deg") {
        PolarLightCell(
            it,
            directionPoleMapPlanar(Base6Direction3d.Left, Base6Direction3d.Right),
            LightVariantType.Cone45Deg,
            ElectricalSize.Standard
        )
    }

    val POLAR_LIGHT_CELL_CONE_SPHERE = cellImmediate("polar_light_sphere") {
        PolarLightCell(
            it,
            directionPoleMapPlanar(Base6Direction3d.Left, Base6Direction3d.Right),
            LightVariantType.Sphere,
            ElectricalSize.Standard
        )
    }

    val TERMINAL_LIGHT_CELL_45DEG = cellImmediate("terminal_light_45deg") {
        TerminalLightCell(it, LightVariantType.Cone45Deg)
    }

    val LIGHT_PART = partImmediateBB("small_wall_lamp", 4.0, 1.0 + 2.302, 5.0) {
        PolarPoweredLightPart(it, POLAR_LIGHT_CELL_CONE_45DEG.get())
    }

    val LIGHT_PART_MICRO_GRID = partAndItemWithProvider(
        "small_wall_lamp_micro_grid",
        BasicPartProvider.setup(Vector3d(8.0 / 16.0, (1.0 + 2.302) / 16.0, 5.0 / 16.0)) {
            val size = Vector3d(0.5 / 16.0, 2.0 / 16.0, 1.0 / 16.0)
            val neg = BoundingBox3d.fromCenterSize((Vector3d(3.75 / 16.0, 0.0, 7.5 / 16.0)) - Vector3d.one * maskXY / 2.0 + size * maskXY / 2.0, size)
            val pos = BoundingBox3d.fromCenterSize((Vector3d(11.75 / 16.0, 0.0, 7.5 / 16.0)) - Vector3d.one * maskXY / 2.0 + size * maskXY / 2.0, size)

            PartFactory { ci ->
                TerminalPoweredLightPart(
                    ci,
                    TERMINAL_LIGHT_CELL_45DEG.get(),
                    neg, pos
                )
            }
        }
    )

    private fun registerLightBulbPR(
        name: String,
        powerRating: Quantity<Power>,
        resistance: Quantity<Resistance>,
        damageRate: Double,
        strength: Double,
        increments: Int = 128,
        baseRadius: Int = 1
    ) : RegistryObject<LightBulbItem> {
        val itemEntry = item(name) {
            val model = LightModel(
                temperatureFunction = {
                    it.power / !powerRating
                },
                resistanceFunction = {
                    resistance
                },
                damageFunction = { v, dt ->
                    dt * (v.power / !powerRating) * damageRate
                },
                volumeProvider45Deg = LightFieldPrimitives.coneContentOnly(
                    increments,
                    strength,
                    PI / 4.0,
                    baseRadius
                ),
                volumeProviderSphere = LightFieldPrimitives.sphere(
                    increments,
                    strength,
                    baseRadius.toDouble()
                )
            )

            LightBulbItem(model)
        }

        CreativeTabRegistry.creativeTabVariant {
            itemEntry.get().createStack(
                life = 1.0,
                count = 1
            )
        }

        return itemEntry
    }

    private fun registerLightBulbPP(
        name: String,
        powerRating: Quantity<Power>,
        potentialRating: Quantity<Potential>,
        damageRate: Double,
        strength: Double,
        increments: Int = 128,
        baseRadius: Int = 1
    ) = registerLightBulbPR(
        name,
        powerRating,
        Quantity((!potentialRating).pow(2) / !powerRating, OHM),
        damageRate,
        strength,
        increments,
        baseRadius
    )

    val LIGHT_BULB_240V_100W = registerLightBulbPP(
        "light_bulb_240v_100w",
        powerRating = Quantity(100.0, WATT),
        potentialRating = Quantity(240.0, VOLT),
        damageRate = 1e-6,
        strength = 24.0
    )


    private fun gardenLightModel(strength: Double) = SolarLightModel(
        solarScan(Vector3d.unitY),
        dischargeRate = 1.0 / 12000.0 * 0.9,
        LightFieldPrimitives.sphere(1, strength)
    )

    val SMALL_GARDEN_LIGHT = partMemoizeBB("small_garden_light", 4.0, 6.0, 4.0) {
        val model = gardenLightModel(3.0)

        PartFactory {
            SolarLightPart(it, model)
        }
    }

    val TALL_GARDEN_LIGHT = partMemoizeBB("tall_garden_light", 3.0, 15.5, 3.0) {
        val model = gardenLightModel(5.0)

        PartFactory {
            SolarLightPart(it, model)
        }
    }

    val LAMP_POLE_BLOCK_DELEGATE_MAP = defineDelegateMap("lamp_pole") {
        val column = registerDelegate(
            0.325, 0.0, 0.325,
            0.675, 1.0, 0.675
        )

        val lamp = registerDelegate(
            0.25, 0.0, 0.25,
            0.75, 0.65, 0.75
        )

        principal(0, 2, 0, column)
        principal(0, 3, 0, column)
        principal(0, 4, 0, column)
        principal(0, 5, 0, lamp)
    }

    val LAMP_POLE_BLOCK = blockOnly("lamp_pole") {
        LampPoleBlock(POLAR_LIGHT_CELL_CONE_SPHERE, BlockPos(0, 5, 0))
    }.withSelfDrop()

    val LAMP_POLE_BLOCK_ENTITY = blockEntityOnly("lamp_pole", LAMP_POLE_BLOCK) { pos, state ->
        LampPoleBlockEntity(pos, state)
    }

    val LAMP_POLE_BLOCK_ITEM = blockItemOnly("lamp_pole") {
        BigBlockItem(
            LAMP_POLE_BLOCK_DELEGATE_MAP.value,
            LAMP_POLE_BLOCK.get()
        )
    }
}
