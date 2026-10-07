@file:Suppress("unused")

package org.eln2.mc.common.content.processing

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import kotlinx.serialization.Serializable
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.geometry.Rotation2d
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Pole
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.*
import org.ageseries.libage.sim.kinetic.FrictionKineticNode
import org.ageseries.libage.sim.kinetic.KineticDouble
import org.ageseries.libage.sim.kinetic.KineticNodeSet
import org.ageseries.libage.sim.kinetic.KineticSimulationForestBuilder
import org.eln2.mc.*
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.ReplaceVanillaParticlesBlockExtension
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.containers.ProgressContainerData
import org.eln2.mc.common.content.DcMotorOptions
import org.eln2.mc.common.content.ThermalWireObject
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.recipes.foundation.*
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Axis3d
import org.eln2.mc.mathematics.Base6Direction3d
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.withSign

/**
 * Abstraction for (single-block) machines that have both kinetic and electrical variants, and possibly multiple tiers of those variants.
 * The machine implements one block and one block entity, that has all the needed recipe and A/V logic.
 * It controls everything using [processingSpeed].
 *
 * To implement multiple variants, we define generic "work boxes", which are cells such as [KineticProcessingCell] and [MotorProcessingCell], with different parameters (tiers).
 * These boxes are then wrapped in a craftable item, that is reused for all machines.
 * Then, we register the specific block and block entity for our machine with the work box cells (by changing the cell provider).
 * Finally, we add a recipe for the base machine, that isn't placeable and doesn't work by itself, but we define a recipe taking the base machine and one of the work boxes, and this creates one of those registered variants which we can place and use.
 * TODO I will implement this automatically with datagen, gotta update this documentation
 * */
abstract class ProcessingCell(ci: CellCreateInfo) : Cell(ci) {
    /**
     * Set by the game object when processing is needed.
     * */
    @CrossThreadAccess
    @OnServerThread
    var isActive: Boolean = false

    /**
     * Read by the game object and used to advance the recipe.
     * */
    @CrossThreadAccess
    @OnServerThread
    var processingSpeed: Double = 0.0
        protected set

    /**
     * Processing speed, signed with the direction.
     * */
    val signedProcessingSpeed: Double
        get() = processingSpeed * if(direction == ProcessingDirection.Forward) 1.0 else -1.0

    /**
     * Read by the game object and used for rendering. For the kinetic work box, this should match the node exactly, so the shaft seems rigidly connected.
     * For the electrical variant, this should be some emulation of the internal motor's rotation, but it would usually be ignored and the speed would be used for any animations.
     * */
    @CrossThreadAccess @OnServerThread
    abstract val kineticState: RotatingKineticState

    /**
     * Power set by the block entity.
     * The work box will try to extract this amount of mechanical power, as the load.
     * */
    @CrossThreadAccess @OnServerThread
    var loadPower: Quantity<Power> = Quantity(0.0, WATT)

    /**
     * Factor set by the block entity. It is used to calculate the portion of simulated work that gets converted into heat.
     * The simulated work is friction, in both the kinetic box and the electrical box.
     * For example, the extruder would have a small thermal factor, since there isn't much friction. But the crusher, for example, would have a much larger factor.
     * */
    @CrossThreadAccess @OnServerThread
    var thermalFactor: Double = 1.0

    enum class ProcessingDirection {
        Forward,
        Reverse;

        companion object {
            fun of(angularVelocity: Double) = if(angularVelocity >= 0.0) Forward else Reverse
        }
    }

    /**
     * The direction currently being imposed by the external device. For electrical boxes, the polarity will set this, and for kinetic boxes, the rotation direction will set this.
     * */
    var direction: ProcessingCell.ProcessingDirection = ProcessingCell.ProcessingDirection.Forward
        protected set

    @Replicator
    fun speedReplicator(target: ProcessingMachineBlockEntity<*>) = ProcessingCellSpeedReplicator(
        this::signedProcessingSpeed,
        target
    )

    /**
     * Replicator for the processing speed (handled by [ProcessingMachineBlockEntity.onSignedProcessingSpeedChanged]).
     * */
    class ProcessingCellSpeedReplicator(val signedSupplier: Supplier<Double>, val consumer: ProcessingMachineBlockEntity<*>) : ReplicatorBehavior {
        var signedTrackedSpeed = 0.0

        override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
            subscribers.addSubscriber(SubscriberOptions(10, SimulationPhase.Post), this::tick)
        }

        private fun tick(dt: Double, phase: SimulationPhase) {
            val signedTargetSpeed = signedSupplier.get()

            if(signedTargetSpeed.sign == signedTrackedSpeed.sign && signedTargetSpeed.approxEq(signedTrackedSpeed, 0.01)) {
                return
            }

            signedTrackedSpeed = signedTargetSpeed
            consumer.onSignedProcessingSpeedChanged(signedTargetSpeed)
        }
    }
}

/**
 * The standard connection directions used for all work box cells.
 * */
val PROCESSING_CELL_POLE_MAP =  directionPoleMapPlanar(
    Base6Direction3d.Left,
    Base6Direction3d.Right
)

//#region Work Box Parameters

data class ProcessingCellThermalOptions(
    val massDef: ThermalMassDefinition,
    val leakageParameters: ConnectionParameters,
    val destroyTemperature: Quantity<Temperature>,
)

/**
 * Work box modeled as a friction load.
 * @param friction The friction of the work box (applied at all times).
 * @param nominalAngularVelocity The nominal angular velocity of the device, where the processing speed is 100%.
 * @param maxTorque Torque breaking limit.
 * */
data class KineticProcessingCellOptions(
    val inertia: Quantity<Inertia>,
    val friction: NodeFrictionDescription,
    val nominalAngularVelocity: Quantity<AngularVelocity>,
    val kineticBreakdownVelocity: Quantity<AngularVelocity>,
    val maxTorque: Quantity<Torque>,
    val thermal: ProcessingCellThermalOptions,
)

/**
 * Work box modeled as an electrical motor.
 * @param startAngularVelocity The processing "starts" at this angular velocity. This value should be relatively high; if the motor gets loaded at a low speed, the current will be enormous, the efficiency low and the motor can stall.
 * */
data class MotorProcessingCellOptions(
    val inertia: Quantity<Inertia>,
    val friction: NodeFrictionDescription,
    val idleResistance: Quantity<Resistance>,
    val armatureResistance: Quantity<Resistance>,
    val armatureInductance: Quantity<Inductance>,
    val backEmfConstant: Quantity<MotorBackEmfConstant>,
    val torqueConstant: Quantity<MotorTorqueConstant>,
    val nominalAngularVelocity: Quantity<AngularVelocity>,
    val dielectricBreakdownPotential: Quantity<Potential>,
    val overPowerThreshold: Quantity<Power>,
    val thermal: ProcessingCellThermalOptions,
) {
    companion object {
        /**
         * Creates the motor parameters using [DcMotorOptions.create] and adds some extra values.
         * @param friction Dependent friction.
         * @param ratedPotential The potential we expect the motor to run at.
         * @param ratedPower The power we expect to be able to draw at the nominal potential and speed.
         * @param ratedSpeed The speed the motor hits unloaded at the [ratedPotential].
         * @param efficiency Number describing the approximate electrical energy to mechanical energy conversion efficiency.
         * @param spinUpTime Used to calculate inertia. The motor reaches its nominal speed in approximately this time period, at the rated potential.
         * @param coolingParameters The environment leakage parameters.
         * */
        fun create(
            friction: Double,
            ratedPotential: Quantity<Potential>,
            ratedPower: Quantity<Power>,
            ratedSpeed: Quantity<AngularVelocity>,
            efficiency: Double,
            spinUpTime: Quantity<Time>,
            mass: ThermalMassDefinition,
            coolingParameters: ConnectionParameters,
            idleResistance: Quantity<Resistance> = Quantity(ElectricalSimulation.MAX_RESISTANCE, OHM),
        ) : MotorProcessingCellOptions {
            val dcMotorOptions = DcMotorOptions.create(
                ratedPotential,
                ratedPower,
                ratedSpeed,
                efficiency,
                spinUpTime
            )

            return MotorProcessingCellOptions(
                dcMotorOptions.frictionNodeDescription.inertia,
                dcMotorOptions.frictionNodeDescription.frictionDescription,
                idleResistance,
                dcMotorOptions.armatureResistance,
                dcMotorOptions.armatureInductance,
                dcMotorOptions.backEmfConstant,
                dcMotorOptions.torqueConstant,
                ratedSpeed,
                dcMotorOptions.breakdownPotential,
                ratedPower * 2.0,
                ProcessingCellThermalOptions(
                    mass,
                    coolingParameters,
                    dcMotorOptions.breakdownTemperature
                )
            )
        }
    }
}

//#endregion

//#region Work Box Implementation

enum class ProcessingCellType {
    Kinetic,
    Electrical
}

/**
 * Represents a registered box cell with an associated item.
 * @param boxType The domain of the implementation (kinetic or electrical).
 * @param cellProvider The registered cell.
 * @param item The associated item.
 * @param prefixToApply A prefix applied to the machine hull, to get the ID of the machine + work box.
 * */
class ProcessingCellRegistryItem<C : ProcessingCell>(
    val boxType: ProcessingCellType,
    val cellProvider: RegistryObject<CellProvider<C>>,
    val item: RegistryObject<Item>,
    val prefixToApply: String,
)

/**
 * Calculates the torque to apply to [node] for the required load power [requiredPower].
 * */
private fun calculateLoadTorque(dt: Double, node: FrictionKineticNode, nominalAngularVelocity: Quantity<AngularVelocity>, requiredPower: Quantity<Power>) : Double {
    var loadTorque = -node.angularVelocity * !requiredPower / (!nominalAngularVelocity * !nominalAngularVelocity)
    val maxTorque = abs(node.angularVelocity) * node.inertia / dt

    if (abs(loadTorque) > maxTorque) {
        loadTorque = maxTorque.withSign(loadTorque)
    }

    return loadTorque
}

/**
 * Calculates the total heat generated, including friction and the conversion factor set by the machine.
 * */
private fun calculateHeating(dt: Double, node: FrictionKineticNode, loadTorque: Double, thermalFactor: Double) : Quantity<Energy> {
    val workEnergy = abs(loadTorque * node.angularVelocity) * dt
    val frictionEnergy = node.deltaHeatFromFriction

    return Quantity(frictionEnergy + thermalFactor * workEnergy, JOULE)
}

/**
 * Kinetic work box. Accepts kinetic connections and models the consumption as friction.
 * */
class KineticProcessingCell private constructor(ci: CellCreateInfo, val options: KineticProcessingCellOptions) : ProcessingCell(ci), SidedKineticMapped<KineticProcessingCell> {
    companion object {
        fun register(name: String, prefix: String, options: KineticProcessingCellOptions) : ProcessingCellRegistryItem<KineticProcessingCell> {
            val cell = CellRegistry.cellImmediate(name) {
                KineticProcessingCell(it, options)
            }

            val item = ItemRegistry.item(name) {
                Item(Item.Properties())
            }

            return ProcessingCellRegistryItem(ProcessingCellType.Kinetic, cell, item, prefix)
        }
    }

    override val kineticMap: PoleMap
        get() = PROCESSING_CELL_POLE_MAP

    override val kineticSize: KineticSize
        get() = KineticSize.Standard

    @SimObject
    val thermalWire = ThermalWireObject(
        this,
        options.thermal.massDef(),
        options.thermal.leakageParameters
    )

    @SimObject
    val kinetic = KineticProcessingObject(this)

    override val kineticState: RotatingKineticState
        get() = RotatingKineticState(kinetic.node.angle, kinetic.node.angularVelocity)

    @Replicator
    fun kineticReplicator(target: InternalKineticStateConsumer) = InternalKineticReplicatorBehavior(
        this::kineticState,
        target,
        this,
        kinetic.node::simulation
    )

    @Behavior
    val thermalBreakdown = ThermalBreakdownBehavior.create(
        options.thermal.destroyTemperature,
        this,
        thermalWire.thermalBody::temperature
    )

    @Behavior
    val kineticBreakdown = KineticBreakdownBehavior.create(options.kineticBreakdownVelocity, this, kinetic.node)

    @Behavior
    val stress = KineticStressBehavior.create(options.maxTorque, this, kinetic.node)

    class KineticProcessingObject(cell: KineticProcessingCell) : KineticObject<KineticProcessingCell>(cell), PersistentObject {
        val node = KineticDouble()

        init {
            node.inertia = !cell.options.inertia
            node.setSafeTorque(cell.options.maxTorque)
            cell.options.friction.applyTo(node)
        }

        override fun offerExtension(remote: KineticObject<*>) = node.chooseExtension(cell.kineticMap, remote)

        override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
            subscribers.addPre(this::tickPre)
        }

        override fun addNodes(builder: KineticNodeSet) {
            builder.add(node)
        }

        /**
         * Converts the input power into some thermal power and updates the [processingSpeed].
         * */
        private fun tickPre(dt: Double, subscriberPhase: SimulationPhase) {
            val options = cell.options

            if(!cell.isActive) {
                cell.processingSpeed = 0.0
                cell.direction = ProcessingDirection.of(0.0)
                return
            }

            val loadTorque = calculateLoadTorque(dt, node, options.nominalAngularVelocity, cell.loadPower)
            val heating = calculateHeating(dt, node, loadTorque, cell.thermalFactor)
            node.externalTorque += loadTorque
            cell.thermalWire.thermalBody.energy += heating
            cell.processingSpeed = abs(node.angularVelocity) / !options.nominalAngularVelocity
            cell.direction = ProcessingCell.ProcessingDirection.of(node.angularVelocity)
            cell.setChanged()
        }

        override fun saveObjectNbt() = node.saveNbt()
        override fun loadObjectNbt(tag: CompoundTag) = node.loadNbt(tag)
    }
}

/**
 * DC motor work box. Accepts electrical connections and models the consumption as the internal inefficiency of the motor, plus velocity-dependent friction.
 * */
class MotorProcessingCell private constructor(ci: CellCreateInfo, val options: MotorProcessingCellOptions) : ProcessingCell(ci), SidedElectricalMapped<MotorProcessingCell> {
    companion object {
        fun register(name: String, prefix: String, options: MotorProcessingCellOptions) : ProcessingCellRegistryItem<MotorProcessingCell> {
            val cell = CellRegistry.cellImmediate(name) {
                MotorProcessingCell(it, options)
            }

            val item = ItemRegistry.item(name) {
                Item(Item.Properties())
            }

            return ProcessingCellRegistryItem(ProcessingCellType.Electrical, cell, item, prefix)
        }
    }

    override val electricalMap: PoleMap
        get() = PROCESSING_CELL_POLE_MAP

    override val electricalSize: ElectricalSize
        get() = ElectricalSize.Any

    @SimObject
    val thermalWire = ThermalWireObject(
        this,
        options.thermal.massDef(),
        options.thermal.leakageParameters
    )

    @SimObject
    val motor = MotorProcessingObject(this)

    override val kineticState: RotatingKineticState
        get() = RotatingKineticState(motor.node.angle, motor.node.angularVelocity)

    @Replicator
    fun kineticReplicator(target: InternalKineticStateConsumer) = InternalKineticReplicatorBehavior(
        this::kineticState,
        target,
        this,
        null
    )

    @Behavior
    val temperatureExplosion = ThermalBreakdownBehavior.create(
        options.thermal.destroyTemperature,
        this,
        thermalWire.thermalBody::temperature
    )

    @Behavior
    val dielectricBreakdown = DielectricBreakdownBehavior.create(this).also {
        it.addPort(
            motor.armatureResistor,
            !options.dielectricBreakdownPotential,
            !options.dielectricBreakdownPotential
        )
    }

    @Behavior
    val overPower = OverPowerBehavior.create(options.overPowerThreshold, this) {
        motor.armatureResistor.power
    }

    class MotorProcessingObject(cell: MotorProcessingCell) : ElectricalObject<MotorProcessingCell>(cell), PersistentObject {
        val armatureResistor = Resistor()
        val armatureInductor = Inductor()
        val potentialSource = PotentialSource()

        val node = KineticDouble()

        val simulation = KineticSimulationForestBuilder()
            .apply { add(node) }
            .build(CellGraph.DT)
            .solvers[0]

        init {
            armatureResistor.resistance = !cell.options.idleResistance
            armatureInductor.inductance = !cell.options.armatureInductance
            potentialSource.potential = 0.0
        }

        override fun offerPolar(remote: ElectricalObject<*>) = when(cell.electricalMap.evaluate(cell, remote.cell)) {
            Pole.Positive -> armatureResistor.negative
            Pole.Negative -> potentialSource.negative
        }

        override fun addComponents(circuit: ElectricalComponentSet) {
            circuit.add(armatureResistor, armatureInductor, potentialSource)
        }

        override fun build(map: ElectricalConnectivityMap) {
            super.build(map)

            map.join(armatureResistor.positive, armatureInductor.positive)
            map.join(armatureInductor.negative, potentialSource.positive)
        }

        override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
            subscribers.addPre(this::tickPre)
            subscribers.addPost(this::tickPost)
        }

        private fun tickPre(dt: Double, phase: SimulationPhase) {
            val options = cell.options

            val loadTorque = calculateLoadTorque(dt, node, options.nominalAngularVelocity, cell.loadPower)
            val heating = calculateHeating(dt, node, loadTorque, cell.thermalFactor)
            node.externalTorque += loadTorque
            cell.thermalWire.thermalBody.energy += heating

            /**
             * On-off switch:
             * */
            armatureResistor.resistance = if(cell.isActive) {
                !options.armatureResistance
            }
            else {
                !options.idleResistance
            }

            /**
             * Back-EMF:
             * */
            potentialSource.potential = !options.backEmfConstant * node.angularVelocity
        }

        private fun tickPost(dt: Double, phase: SimulationPhase) {
            val options = cell.options

            simulation.step()

            /**
             * Torque for the current across the device:
             * */
            val torque = armatureResistor.current * !cell.options.torqueConstant
            node.externalTorque -= torque

            /**
             * Integrates the resistive heating:
             * */
            cell.thermalWire.thermalBody.energy += Quantity(abs(armatureResistor.power) * dt)

            if(!torque.approxEq(0.0, 1e-5) || !node.frictionTorque.approxEq(0.0, 1e-5)) {
                cell.setChanged()
            }

            if(cell.isActive) {
                cell.processingSpeed = abs(node.angularVelocity) / !options.nominalAngularVelocity
                cell.direction = ProcessingCell.ProcessingDirection.of(node.angularVelocity)
            }
            else {
                cell.processingSpeed = 0.0
                cell.direction = ProcessingCell.ProcessingDirection.of(0.0)
            }
        }

        override fun saveObjectNbt() : CompoundTag {
            val tag = CompoundTag()

            tag.put(INDUCTOR, armatureInductor.saveNbt())
            tag.put(NODE, node.saveNbt())

            return tag
        }

        override fun loadObjectNbt(tag: CompoundTag) {
            armatureInductor.loadNbt(tag.getCompound(INDUCTOR))
            node.loadNbt(tag.getCompound(NODE))
        }

        companion object {
            private const val INDUCTOR = "inductor"
            private const val NODE = "node"
        }
    }
}

//#endregion

/**
 * @param C The work box cell, which abstracts away as a [ProcessingCell].
 * */
abstract class ProcessingMachineBlock<C : ProcessingCell, BE : ProcessingMachineBlockEntity<C>>(val cell: RegistryObject<CellProvider<C>>) : UprightHorizontalDirectionCellBlock<C>() {
    override fun getCellProvider() = cell.get()

    abstract fun getTitle() : Component

    abstract fun createMenu(pBlockEntity: BE, pContainerId: Int, pPlayerInventory: Inventory) : AbstractContainerMenu

    @Deprecated("Deprecated in Java", ReplaceWith("true"))
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction) = true

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    override fun <T : BlockEntity?> getTicker(pLevel: Level, pState: BlockState, pBlockEntityType: BlockEntityType<T?>): BlockEntityTicker<T> {
        return BlockEntityTicker { pLevel: Level?, _: BlockPos?, _: BlockState?, pBlockEntity: BlockEntity? ->
            if (pLevel == null || pBlockEntity == null) {
                return@BlockEntityTicker DEBUGGER_BREAK()
            }

            if (pBlockEntity !is ProcessingMachineBlockEntity<*>) {
                LOG.error(DEBUGGER_BREAK("Got $pBlockEntity instead of machine block entity"))
                return@BlockEntityTicker
            }

            if (pLevel.isClientSide) {
                pBlockEntity.clientTick()
            }
            else {
                pBlockEntity.serverTick()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        return pLevel.constructMenuHelper2<BlockEntity>(
            pPos,
            pPlayer,
            getTitle()
        ) { a, b, c ->
            @Suppress("UNCHECKED_CAST")
            createMenu(a as BE, b, c)
        }
    }

    /**
     * Called by [animateTick], to animate particles.
     * @param avSpeed Smooth speed from [ProcessingMachineBlockEntity.ClientState.avSpeedSmoother].
     * */
    open fun animateMachineTick(blockEntity: BE, processingDirection: ProcessingCell.ProcessingDirection, avSpeed: Double, pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) { }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        @Suppress("UNCHECKED_CAST") val blockEntity = pLevel.getBlockEntity(pPos) as? BE
            ?: return

        val speed = blockEntity.clientState!!.avSpeedSmoother.value

        if(speed < 0.01) {
            return
        }

        animateMachineTick(
            blockEntity,
            blockEntity.clientState!!.processingDirection, speed,
            pState, pLevel, pPos, pRandom
        )
    }

    //#region Collider

    open fun getColliderFor(pState: BlockState, pLevel: BlockGetter, pPos: BlockPos, pContext: CollisionContext) : VoxelShape? = null

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getColliderFor(pState, pLevel, pPos, pContext) ?: super.getCollisionShape(pState, pLevel, pPos, pContext)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getColliderFor(pState, pLevel, pPos, pContext) ?: super.getShape(pState, pLevel, pPos, pContext)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getVisualShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getColliderFor(pState, pLevel, pPos, pContext) ?: super.getVisualShape(pState, pLevel, pPos, pContext)

    //#endregion
}

/**
 * Base machine with no processing logic. It only has replication logic and sound logic (supports a single looping sound, see [getSound]).
 * */
abstract class ProcessingMachineBlockEntity<C : ProcessingCell>(pPos: BlockPos, pState: BlockState) :
    CellBlockEntity<C>(pPos, pState, BlockRegistry.getBlockEntityType(pState.block).get()),
    BulkPacketHandlerBlockEntity,
    InternalKineticStateConsumer,
    ComponentDisplay
{
    //#region Client State

    /**
     * Created in [setLevel] on the client side.
     * */
    var clientState: ClientState? = null
        private set

    @ClientOnly
    class ClientState {
        /**
         * The raw processing speed, sent by the replicator.
         * */
        var targetClientSpeed = 0.0

        /**
         * The raw kinetic state, sent by the replicator.
         * */
        var targetClientKineticState: RotatingKineticState = RotatingKineticState(0.0, 0.0)

        var renderVersion = 0

        /**
         * Set in the first [clientTick], if [getSound] returns a non-null sound event.
         * */
        var soundInstance: SimpleLoopingBlockEntitySoundInstance<BlockEntity>? = null

        /**
         * Smoother updated in [clientTick] using [ClientState.targetClientSpeed], for **audio and particles effects only**.
         * */
        val avSpeedSmoother = FramerateIndependentSmoother1d(0.2)

        var processingDirection = ProcessingCell.ProcessingDirection.Forward
    }

    /**
     * Sets up the [clientState].
     * */
    override fun setLevel(pLevel: Level) {
        if(pLevel.isClientSide) {
            clientState = ClientState()
        }

        super.setLevel(pLevel)
    }

    //#endregion

    /**
     * Called on the first [clientTick].
     * If this machine uses a single, speed-based looping sound, return the sound here, and it will be played and controlled with [ClientState.avSpeedSmoother].
     * */
    open val sound: RegistryObject<SoundEvent>?
        get() = null

    //#region Tick Loops

    @ServerOnly
    open fun serverTick() { }

    /**
     * Method called by [clientTick], used to update A/V, if needed.
     * @param avSpeed The smoothed speed, from [ClientState.avSpeedSmoother].
     * */
    open fun animateClientTick(dt: Double, state: ClientState, processingDirection: ProcessingCell.ProcessingDirection, avSpeed: Double, level: ClientLevel) { }

    @ClientOnly
    fun clientTick() {
        val state = clientState!!
        val dt = state.avSpeedSmoother.update(clientState!!.targetClientSpeed)

        if(state.avSpeedSmoother.value < 0.01) {
            return
        }

        val level = level as? ClientLevel
            ?: return

        if(clientState!!.soundInstance == null) {
            val soundEvent = sound

            if(soundEvent != null) {
                clientState!!.soundInstance = SimpleLoopingBlockEntitySoundInstance(this as BlockEntity, soundEvent.get()).also {
                    it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                        it.soundInfo = SoundInfo.standardWithProcessingSpeed(state.avSpeedSmoother.value)
                    }

                    it.registerOnAudioManager()
                }
            }
        }

        animateClientTick(dt, state, state.processingDirection, state.avSpeedSmoother.value, level)
    }

    //#endregion

    //#region Replication

    override val clientSidePacketHandlerLazy = createClientSideHandler()

    // onSyncSuggested
    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        if(hasCell) {
            this.sendBulkPacket(ProcessingSyncPacket::serialize, ProcessingSyncPacket(cell.signedProcessingSpeed))
            this.sendBulkPacket(RotatingKineticState::serialize, cell.kineticState)
        }

        return super.getUpdateTag()
    }

    open fun onSyncSuggested() { }

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        fun modifyState(block: ClientState.() -> Unit) {
            val state = clientState!!
            block.invoke(state)
            state.renderVersion++
        }

        handler.withHandler<ProcessingSyncPacket>(ProcessingSyncPacket::deserialize) { packet ->
            modifyState {
                targetClientSpeed = abs(packet.signedSpeed)
                processingDirection = if(packet.signedSpeed >= 0.0) ProcessingCell.ProcessingDirection.Forward else ProcessingCell.ProcessingDirection.Reverse
            }
        }

        handler.withHandler<RotatingKineticState>(RotatingKineticState::deserialize) { state ->
            modifyState {
                targetClientKineticState = state
            }
        }
    }

    @OnSimulationThread
    fun onSignedProcessingSpeedChanged(newSpeed: Double) {
        sendBulkPacket(ProcessingSyncPacket::serialize, ProcessingSyncPacket(newSpeed))
    }

    @OnSimulationThread
    override fun onKineticStateChanged(state: RotatingKineticState) {
        sendBulkPacket(RotatingKineticState::serialize, state)
    }

    class ProcessingSyncPacket(val signedSpeed: Double) {
        companion object {
            fun serialize(packet: ProcessingSyncPacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.signedSpeed)
            }

            fun deserialize(buffer: FriendlyByteBuf) = ProcessingSyncPacket(
                buffer.readDouble()
            )
        }
    }

    //#endregion

    override fun submitDisplay(builder: ComponentDisplayList) {
        val cell = cell

        builder.debugInIDE { "Processing Speed: ${cell.processingSpeed.rounded()}" }
        builder.debugInIDE { "Direction: ${cell.direction}" }

        if(cell is KineticProcessingCell) {
            cell.kinetic.subSolvers?.debugInIDE(builder)
            builder.debugInIDE { "Angular velocity: ${cell.kinetic.node.angularVelocity.rounded()}" }
            builder.quantity(cell.thermalWire.thermalBody.temperature)
        }
        else if(cell is MotorProcessingCell) {
            builder.debugInIDE { "Angular velocity: ${cell.motor.node.angularVelocity.rounded()}" }
            builder.debugInIDE { "Speed: ${cell.processingSpeed.rounded()}" }
            builder.debugInIDE { "Back-EMF: ${cell.motor.potentialSource.potential.rounded()}" }
            builder.debugInIDE { "Resistor power: ${cell.motor.armatureResistor.power.rounded()}"}
            builder.quantity(cell.thermalWire.thermalBody.temperature)
        }
    }
}

/**
 * Work box machine with a simple item processing loop.
 * @param inventorySize The size of the created inventory handler.
 * */
abstract class SimpleProcessingMachineBlockEntity<C : ProcessingCell, R : Eln2SimpleOutputProcessingLoopRecipe>(
    pPos: BlockPos,
    pState: BlockState,
    val inventorySize: Int
) : ProcessingMachineBlockEntity<C>(pPos, pState) {
    /**
     * Gets the recipe applied by the machine. Must return the same value each time.
     * */
    abstract val recipe: RecipeType<R>

    /**
     * Gets the slots holding the input items, that will be consumed.
     * */
    open val inputSlots: IntArray = intArrayOf(INPUT_SLOT)

    /**
     * If true, the recipe loop will be stepped when [ProcessingCell.direction] is reverse too.
     * */
    open val allowProcessingInReverse: Boolean
        get() = true

    //#region Capability

    val inventoryHandler = SimpleProcessingRecipeInventoryHandler.create(this, recipe, inventorySize, inputSlots)
    val inventoryHandlerLazy: LazyOptional<SimpleProcessingRecipeInventoryHandler<R>> = LazyOptional.of { inventoryHandler }

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        inventoryHandlerLazy.invalidate()
    }

    //#endregion

    //#region Logic

    /**
     * Container with the progress parameter for the GUI.
     * */
    @ServerOnly
    val data = ProgressContainerData()

    /**
     * The server state of the processing.
     * */
    @ServerOnly
    val loop = ProcessingRecipeLoop.create<R>(this)

    /**
     * Implementation of [ProcessingDevice] which multiplies the cell's processing speed by the device's factor.
     * */
    open class RecipeWrapper(val cell: ProcessingCell, val factor: Double, override val tier: Int) : TieredProcessingDevice {
        override var isActive: Boolean
            get() = cell.isActive
            set(value) { cell.isActive = value }

        override val processingSpeed: Double
            get() = if(pauseProcessing) 0.0 else cell.processingSpeed * factor

        var pauseProcessing = false
    }

    protected var wrapper: RecipeWrapper? = null

    protected fun setDefaultRecipeOptions(factor: Double, tier: Int = Int.MAX_VALUE) {
        wrapper = RecipeWrapper(cell, factor, tier)
    }

    private var initialized = false

    /**
     * Called in the first [serverTick] to set the power and create the processing cell wrapper.
     * Used if your machine will have different tiers. Let's say the tier is defined by the required power and a speed factor (and say, a max recipe tier of some sort).
     * You pass along that data in the constructor, and:
     * - Set [ProcessingCell.loadPower]
     * - Call [setDefaultRecipeOptions] with your speed and tier, which sets the [wrapper] that is used by the [loop]
     * */
    @CalledOnce @OnServerThread
    open fun loadSettings() {
        setDefaultRecipeOptions(1.0)
    }

    @ServerOnly
    protected open fun tickRecipe() {
        val wrapper = wrapper!!

        wrapper.pauseProcessing = if(cell.direction == ProcessingCell.ProcessingDirection.Forward) {
            false
        }
        else {
            !allowProcessingInReverse
        }

        val result = loop.tick(wrapper, inventoryHandler)
        data.progress = loop.lastProgress.toFloat()
    }

    /**
     * Initializes the cell and ticks the recipe.
     * */
    @ServerOnly
    override fun serverTick() {
        if(!initialized) {
            initialized = true

            loadSettings()

            check(wrapper != null) {
                DEBUGGER_BREAK("Did not initialize wrapper for the processing cell")
            }
        }

        tickRecipe()
    }

    //#endregion

    //#region Saving

    @ServerOnly
    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put("inventory", inventoryHandler.serializeNBT())
        loop.saveAdditional(pTag)
    }

    @ServerOnly
    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        inventoryHandler.deserializeNBT(pTag.getCompound("inventory"))
        loop.load(pTag)
    }

    //#endregion
}

/**
 * @param body The base model of the machine, same for both kinetic and electric variants.
 * @param defineRotatingElements Lazy builder for animated rotating elements (currently constrained to rotating around one axis, will upgrade if needed).
 * */
class ProcessingMachineCompositeModel(val body: PartialModel, private val defineRotatingElements: RotatingElementListBuilder.() -> Unit) {
    constructor(body: PartialModel) : this(body, { })

    private val rotatingElementsLazy = lazy {
        val builder = RotatingElementListBuilder()
        defineRotatingElements(builder)
        builder.elements
    }

    val rotatingElements: List<RotatingElement> by rotatingElementsLazy

    class RotatingElementListBuilder {
        val elements = ArrayList<RotatingElement>()

        fun withElement(model: PartialModel, factor: Double, axis: Axis3d = Axis3d.X) {
            elements.add(RotatingElement(model, factor, axis))
        }
    }

    /**
     * Represents a wheel or gear that is rotating based on the processing speed of the machine.
     * @param model The model, in the desired position and orientation in the model space.
     * @param factor Angular velocity multiplier. The perceived angular velocity of the element will be [factor]*`speed`, where `speed` is the processing speed of the machine.
     * */
    class RotatingElement(val model: PartialModel, val factor: Double, val rotationAxis: Axis3d) {
        val center = FlwModels.getModelCenter(model)
    }
}

/**
 * Handles rendering both kinetic and electrical variants of work box machines.
 * For kinetic variants, a shaft instance is automatically created and animated. For electrical variants, a wire instance is automatically created.
 * @param workBox The domain of the work box cell.
 * @param composite The model, containing the hull and the extra animated elements.
 * */
open class ProcessingMachineBlockEntityVisual<C : ProcessingCell, BE : ProcessingMachineBlockEntity<C>>(
    ctx: VisualizationContext,
    blockEntity: BE,
    partialTick: Float,
    workBox: ProcessingCellType,
    composite: ProcessingMachineCompositeModel
) : AbstractBlockEntityVisual<BE>(ctx, blockEntity, partialTick), SimpleDynamicVisual, ShaderLightVisual {
    companion object {
        val SHAFT_CENTER = FlwModels.getModelCenter(FlwModels.WORK_BOX_COMPOSITE_SHAFT)
    }

    /**
     * The body instance, created regardless of work box type.
     * */
    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(
            InstanceTypes.TRANSFORMED,
            PartialModelHelper.applyMaterial(
                composite.body,
                FlwMaterials.SMOOTH_LIT
            )
        )
        .createInstance()
        .transformFacingBlock(visualPos, blockEntity)

    interface WorkboxSpecificData {
        val instance: TransformedInstance
    }

    class KineticData(override val instance: TransformedInstance) : WorkboxSpecificData {
        var version = 0
        val interpolator = KineticInterpolatorClient()
    }

    class ElectricalData(override val instance: TransformedInstance) : WorkboxSpecificData

    val specificData: WorkboxSpecificData = when(workBox) {
        ProcessingCellType.Kinetic -> {
            KineticData(
                visualizationContext.instancerProvider()
                    .instancer(
                        InstanceTypes.TRANSFORMED,
                        PartialModelHelper.applyMaterial(
                            FlwModels.WORK_BOX_COMPOSITE_SHAFT,
                            FlwMaterials.SMOOTH_LIT
                        )
                    )
                    .createInstance().also {
                        poseShaft(it, SHAFT_CENTER, 0.0)
                    }
            )
        }
        ProcessingCellType.Electrical -> {
            ElectricalData(
                visualizationContext.instancerProvider()
                    .instancer(
                        InstanceTypes.TRANSFORMED,
                        PartialModelHelper.applyMaterial(
                            FlwModels.WORK_BOX_COMPOSITE_CABLE,
                            FlwMaterials.SMOOTH_LIT
                        )
                    )
                    .createInstance()
                    .transformFacingBlock(visualPos, blockEntity)
            )
        }
    }

    class RotatingElement(val instance: TransformedInstance, val center: Vector3d, val factor: Double, val axis: Axis3d)

    /**
     * Instances of the animated rotating elements.
     * */
    val rotatingElements = composite.rotatingElements.map { element ->
        val instance = visualizationContext.instancerProvider()
            .instancer(
                InstanceTypes.TRANSFORMED,
                PartialModelHelper.applyMaterial(
                    element.model,
                    FlwMaterials.SMOOTH_LIT
                )
            )
            .createInstance().also {
                poseShaft(it, element.center, 0.0)
            }

        RotatingElement(instance, element.center, element.factor, element.rotationAxis)
    }.toTypedArray()

    /**
     * Rotation and smoother for the [ProcessingMachineBlockEntity.ClientState.targetClientSpeed], which is used for the rotating elements in [ProcessingMachineCompositeModel.rotatingElements].
     * */
    var processRotation = Rotation2d.identity
    val processSpeedSmoother = FramerateIndependentSmoother1d(0.1)

    open fun executeFrame(dt: Double, renderState: ProcessingMachineBlockEntity.ClientState, processSpeed: Double) { }

    final override fun beginFrame(p0: DynamicVisual.Context?) {
        val renderState = blockEntity.clientState!!
        val processSpeed = renderState.targetClientSpeed

        val dt = processSpeedSmoother.update(processSpeed)
        processSpeedSmoother.pullDown()
        val processIncr = processSpeedSmoother.value * dt * if(renderState.processingDirection == ProcessingCell.ProcessingDirection.Forward) 1.0 else -1.0

        if(processIncr != 0.0) {
            processRotation += processIncr
            val angle = processRotation.ln()

            for(i in 0 until rotatingElements.size) {
                val element = rotatingElements[i]

                poseShaft(element.instance, element.center, angle * element.factor, element.axis)
            }
        }

        val specificData = specificData
        if(specificData is KineticData) {
            val targetVersion = renderState.renderVersion
            if(specificData.version != targetVersion) {
                specificData.version = targetVersion

                specificData.interpolator.applyServerState(
                    renderState.targetClientKineticState.angle,
                    renderState.targetClientKineticState.angularVelocity
                )
            }

            specificData.interpolator.update()
            poseShaft(specificData.instance, SHAFT_CENTER, -specificData.interpolator.clientRotation.ln())
        }

        executeFrame(dt, renderState, processSpeed)
    }

    override fun updateLight(p0: Float) {
        relight(body, specificData.instance)
        relight(rotatingElements.map { it.instance })
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(body)
        p0.accept(specificData.instance)

        rotatingElements.forEach {
            p0.accept(it.instance)
        }
    }

    override fun _delete() {
        body.delete()
        specificData.instance.delete()

        rotatingElements.forEach {
            it.instance.delete()
        }
    }

    fun poseShaft(instance: TransformedInstance, center: Vector3d, rotation: Double, axis: Axis3d = Axis3d.X) {
        val mask = axis.maskExclude
        val x = mask.x * center.x
        val y = mask.y * center.y
        val z = mask.z * center.z

        instance.setIdentityTransform()
            .translate(visualPos)
            .center()
            .rotateToFace(blockEntity.blockState.getValue(HorizontalDirectionalBlock.FACING))
            .uncenter()
            .translate(x, y, z)
            .apply {
                when(axis) {
                    Axis3d.X -> rotateX(rotation.toFloat())
                    Axis3d.Y -> rotateY(rotation.toFloat())
                    Axis3d.Z -> rotateZ(rotation.toFloat())
                }
            }
            .translate(-x, -y, -z)
            .setChanged()
    }
}
