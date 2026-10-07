@file:Suppress("unused", "RedundantSamConstructor")

package org.eln2.mc.common.content.modules

import dev.engine_room.flywheel.api.visualization.VisualizerRegistry
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.geometry.BoundingBox3d
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.utils.addUnique
import org.eln2.mc.FrictionNodeDescription
import org.eln2.mc.NodeFrictionDescription
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.BasicPartVisual
import org.eln2.mc.client.render.foundation.DummyBlockEntityRendererProvider
import org.eln2.mc.client.render.foundation.FlwVisualizerRegistry.setPartVisualizer
import org.eln2.mc.common.blocks.BlockRegistry.blockEntityOnly
import org.eln2.mc.common.blocks.BlockRegistry.blockItemOnly
import org.eln2.mc.common.blocks.BlockRegistry.blockOnly
import org.eln2.mc.common.blocks.BlockRegistry.defineDelegateMap
import org.eln2.mc.common.blocks.foundation.BigBlockItem
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.CellRegistry.cellMemoize
import org.eln2.mc.common.cells.foundation.CellFactory
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.ElectricalSize
import org.eln2.mc.common.cells.foundation.KineticSize
import org.eln2.mc.common.content.*
import org.eln2.mc.common.content.modules.ContentManager.withSelfDrop
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.PartRegistry.partImmediateBB
import org.eln2.mc.common.parts.PartRegistry.partMemoizeBB
import org.eln2.mc.common.parts.foundation.PartFactory
import org.eln2.mc.common.sounds.SoundRegistry.soundEventVariableRange
import org.eln2.mc.directionPoleMapPlanar
import org.eln2.mc.extensions.data3D
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.monopolarMapPlanar
import java.util.function.Supplier

object Eln2Kinetic : ContentModule() {
    //#region Registration Helpers

    class JointInfo(
        val id: String,
        val cell: RegistryObject<CellProvider<MultiJointCell>>,
        val partInfo: PartRegistry.PartRegistryItem,
        val model: Supplier<JointPartModel>
    )

    private val JOINTS_FOR_VISUALIZER_REGISTRY = LinkedHashSet<JointInfo>()

    class JointBuilder {
        var baseThermalData = ThermalMassDefinition(
            ChemicalElement.Iron.asMaterial,
            mass = Quantity(40.0, KILOGRAM)
        )

        /**
         * Calculated from the model's 7, 1.4, 1.4 size with iron's density:
         * */
        var shaftMass = Quantity(26.37482, KILOGRAM)

        var mask = Base6Direction3dMask.EMPTY

        val ratios = DoubleArray(6) { 0.0 }

        var kineticSize = KineticSize.Standard

        /**
         * Calculated like the thermal data above:
         * */
        var inertia = Quantity(0.03365, KILOGRAM_METER2)

        var friction = NodeFrictionDescription(
            0.01,
            Quantity(1e-5, NEWTON_METER),
            Quantity(0.1, NEWTON_METER)
        )

        /**
         * Apparently this is about right (with a large margin of error):
         * */
        var maxTorque = Quantity(6000.0, NEWTON_METER)

        var breakdownVelocity = Quantity(100.0, REVOLUTION_PER_SECOND)

        var cooling = ConnectionParameters.DEFAULT

        /**
         * If true, all shafts will be instanced and displayed.
         * */
        var alwaysInstanceShafts = false

        fun withShaft(direction: Base6Direction3d, ratio: Double) {
            mask += direction
            ratios[direction.data3D] = ratio
        }

        class Finished(val modelSupplier: Supplier<JointPartModel>)

        fun finish(modelSupplier: Supplier<JointPartModel>) = Finished(modelSupplier)
    }

    fun joint(id: String, size: Vector3d, build: JointBuilder.() -> JointBuilder.Finished) : JointInfo {
        val builder = JointBuilder()
        val data = build(builder)

        val cell = CellRegistry.cellImmediate(id) { ci ->
            MultiJointCell(
                ci,
                builder.baseThermalData,
                builder.shaftMass,
                builder.mask,
                builder.ratios,
                builder.kineticSize,
                FrictionNodeDescription(
                    builder.inertia,
                    builder.friction
                ),
                builder.breakdownVelocity,
                builder.maxTorque,
                builder.alwaysInstanceShafts,
                builder.cooling
            )
        }

        val part = partImmediateBB(id, size) { ci ->
            JointPart(ci, cell, builder.mask)
        }

        val obj = JointInfo(id, cell, part, data.modelSupplier)
        JOINTS_FOR_VISUALIZER_REGISTRY.addUnique(obj)

        return obj
    }

    private fun registerJointVisualizers() {
        JOINTS_FOR_VISUALIZER_REGISTRY.forEach {
            setPartVisualizer<JointPart>(it.partInfo.part.get()) { ctx, part ->
                JointPartVisual(ctx, part, it.model.get())
            }
        }
    }

    //#endregion

    override fun registerBlockEntityVisualizers() {
        VisualizerRegistry.setVisualizer(
            WIND_TURBINE_BLOCK_ENTITY.get(),
            SimpleBlockEntityVisualizer(::WindTurbineBlockEntityVisual) { true }
        )
    }

    override fun registerPartVisualizers() {
        registerJointVisualizers()

        setPartVisualizer<DcMotorPart>(BASIC_DC_MOTOR_PART.part.get()) { ctx, part ->
            BasicPartVisual(
                ctx, part,
                FlwModels.BASIC_DC_MOTOR
            )
        }
    }

    override fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(
            WIND_TURBINE_BLOCK_ENTITY.get(),
            DummyBlockEntityRendererProvider()
        )
    }

    //#region Joints

    val JOINT_SOUND = soundEventVariableRange("shaft")

    /**
     * Standard straight shaft. Not dynamically instanced, for visual purposes.
     * */
    val PRIMITIVE_STANDARD_IRON_STRAIGHT_JOINT = joint("primitive_standard_iron_straight_joint", Vector3d(6.0, 10.0, 16.0)) {
        withShaft(Base6Direction3d.Front, 1.0)
        withShaft(Base6Direction3d.Back, 1.0)

        alwaysInstanceShafts = true

        finish {
            JointPartModel.homogenous(
                FlwModels.STANDARD_IRON_STRAIGHT_JOINT_BODY,
                FlwModels.STANDARD_IRON_STRAIGHT_JOINT_SHAFT,
                FlwModels.STANDARD_IRON_STRAIGHT_JOINT_SHAFT_BODY
            )
        }
    }

    /**
     * Dynamic 5-ended joint. The mother of all kinetic joints.
     * It is not 6-ended because the part attaches to the substrate via a pedestal, so the system looks more rugged.
     * */
    val PRIMITIVE_STANDARD_IRON_HUB_JOINT = joint("primitive_standard_iron_hub_joint", Vector3d(16.0, 10.0, 16.0)) {
        withShaft(Base6Direction3d.Front, 1.0)
        withShaft(Base6Direction3d.Back, 1.0)
        withShaft(Base6Direction3d.Left, -1.0)
        withShaft(Base6Direction3d.Right, -1.0)
        withShaft(Base6Direction3d.Up, 1.0)

        finish {
            JointPartModel.build(FlwModels.STANDARD_IRON_HUB_JOINT_HUB) {
                Base6Direction3dMask.HORIZONTALS.forEach {
                    withModel(
                        it,
                        FlwModels.STANDARD_IRON_HUB_JOINT_SHAFT,
                        FlwModels.STANDARD_IRON_HUB_JOINT_SHAFT_BODY
                    )
                }

                withModel(
                    Base6Direction3d.Up,
                    FlwModels.STANDARD_IRON_HUB_JOINT_SHAFT,
                    null
                )
            }
        }
    }

    //#endregion

    //#region Motors

    val MOTOR_KINETIC_SOUND = soundEventVariableRange("motor.kinetic")
    val MOTOR_ELECTROMAGNETIC_SOUND = soundEventVariableRange("motor.electromagnetic")

    val WIND_TURBINE_WIND_SOUND = soundEventVariableRange("turbine.wind")
    val WIND_TURBINE_ROTATION_SOUND = soundEventVariableRange("turbine.wind_rotation")

    val BASIC_DC_MOTOR_CELL_DIRECTION = Base6Direction3d.Front

    val BASIC_DC_MOTOR_CELL = cellMemoize("basic_dc_motor") {
        val electricalMap = directionPoleMapPlanar(Base6Direction3d.Left, Base6Direction3d.Right)
        val kineticMap = monopolarMapPlanar(BASIC_DC_MOTOR_CELL_DIRECTION)

        val electricalSize = ElectricalSize.Standard
        val kineticSize = KineticSize.Standard

        val model = DcMotorOptions(
            FrictionNodeDescription(
                Quantity(3.1278, KILOGRAM_METER2),
                0.01,
                Quantity(1.0),
                Quantity(0.1)
            ),
            1e-6,
            Quantity(0.0667, OHM),
            Quantity(1.25, MILLI * HENRY),
            Quantity(2.06, VOLT_PER_RADIAN_PER_SECOND),
            Quantity(2.05, NEWTON_METER_PER_AMPERE),
            Quantity(100.0, REVOLUTION_PER_SECOND),
            Quantity(781.691, VOLT),
            Quantity(173.25, CELSIUS)
        )

        val thermalDef = ThermalMassDefinition(
            ChemicalElement.Iron.asMaterial,
            mass = Quantity(2.1451, KILOGRAM) // Mass of the wiring itself. Not the entire rotor.
        )

        val leakage = ConnectionParameters.DEFAULT

        CellFactory {
            DcMotorCell(it,
                electricalMap, kineticMap,
                electricalSize, kineticSize,
                model,
                thermalDef, leakage
            )
        }
    }

    val BASIC_DC_MOTOR_PART = partMemoizeBB("basic_dc_motor", 16.0, 13.0, 14.3) {
        val soundOptions = DcMotorSoundOptions(
            Quantity(10.0, REVOLUTION_PER_SECOND),
            Quantity(12.0, KILO * WATT)
        )

        PartFactory {
            DcMotorPart(
                it,
                soundOptions,
                BASIC_DC_MOTOR_CELL,
                Base6Direction3dMask.ofRelative(BASIC_DC_MOTOR_CELL_DIRECTION)
            )
        }
    }

    //#endregion

    //#region Wind Turbine

    val BASIC_WIND_TURBINE_CELL = cellMemoize("basic_wind_turbine") {
        val options = WindTurbineOptions(
            BoundingBox3d.fromCenterSize(
                Vector3d.zero,
                16.0
            ),
            BoundingBox3d.fromCenterSize(
                Vector3d.zero,
                Vector3d(36.0 / 16.0, 77.0 / 16.0, 36.0 / 16.0)
            ),
            FrictionNodeDescription(
                Quantity(12.5, KILOGRAM_METER2),
                1.251,
                Quantity(0.01),
                Quantity(0.1)
            ),
            Quantity(1.5, METER),
            0.89159015861095
        )

        CellFactory {
            WindTurbineCell(it, options)
        }

    }

    val BASIC_WIND_TURBINE_DELEGATE_MAP = defineDelegateMap("basic_wind_turbine") {
        val fullBlock = registerDelegate(
            0.0, 0.0, 0.0,
            1.0, 1.0, 1.0
        )

        fun defineSlab(y: Int) {
            for(x in -1..1) {
                for(z in -1..1) {
                    principal(x, y, z, fullBlock)
                }
            }
        }

        defineSlab(1)
        defineSlab(2)
        defineSlab(3)
        defineSlab(4)
        defineSlab(5)
    }

    val BASIC_WIND_TURBINE_BLOCK = blockOnly("basic_wind_turbine") {
        WindTurbineBlock(
            BASIC_WIND_TURBINE_CELL,
            BASIC_WIND_TURBINE_DELEGATE_MAP.value,
            WindTurbine3dModel(
                FlwModels.BASIC_WIND_TURBINE_BASE,
                FlwModels.BASIC_WIND_TURBINE_ROTOR
            )
        )
    }.withSelfDrop()

    val BASIC_WIND_TURBINE_BLOCK_ITEM = blockItemOnly("basic_wind_turbine") {
        BigBlockItem(
            BASIC_WIND_TURBINE_DELEGATE_MAP.value,
            BASIC_WIND_TURBINE_BLOCK.get()
        )
    }

    // Single block entity for all turbines (implement new turbines by adding only a new block)
    val WIND_TURBINE_BLOCK_ENTITY = blockEntityOnly(
        "wind_turbine",
        BASIC_WIND_TURBINE_BLOCK,
        ::WindTurbineBlockEntity
    )

    //#endregion
}
