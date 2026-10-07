@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.eln2.mc.common.content

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.kinetic.KineticDouble
import org.ageseries.libage.sim.kinetic.KineticExtension
import org.ageseries.libage.sim.kinetic.KineticNodeSet
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.*
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.content.modules.Eln2ForgeFluids
import org.eln2.mc.common.content.modules.Eln2SteamTurbine
import org.eln2.mc.common.fluids.foundation.*
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Model parameters for the [SteamTurbineGenerator].
 *
 * @param etaFactor Scales the Carnot efficiency. The actual efficiency is `(1 - T_cold / T_hot) * [etaFactor]`. A value of `0.5` means the turbine achieves half the ideal Carnot efficiency.
 * @param maxFlowRate Maximum steam consumption per tick (mB/sec). This limits the total thermal power the turbine can extract from the steam. Combined with [etaFactor] and the steam enthalpy, this determines the theoretical maximum mechanical output. Should be tuned so that `eta * maxFlowRate * 20 * dH` does not exceed [maxPower], making [maxPower] the effective cap.
 * @param maxTorque Hard cap on the torque applied to the kinetic shaft. At low angular velocities, the torque demanded by the load may exceed what the turbine can mechanically deliver; this prevents unrealistically high startup torque. Should be several times the nominal torque at [referenceAngularVelocity].
 * @param maxPower Hard cap on mechanical output power. Regardless of steam supply or efficiency, the turbine will not produce more than this. This is the primary parameter that bounds the turbine's rating.
 * @param coldSideMass Mass of the cold-side thermal mass. This is the internal heat exchanger / condenser surface that absorbs waste heat (including kinetic friction). A larger mass means slower temperature changes, giving the player more time to react to cooling failures.
 * @param coldSideMaterial Material of the cold-side thermal mass. Determines the specific heat capacity and thus the thermal inertia of the cold side.
 * @param coldSideLeakage Thermal leakage from the cold side to ambient. This is the passive heat loss when no external cooling is connected. Should be small so that active cooling is required.
 * @param referenceAngularVelocity Reference shaft angular velocity for scaling. Used as the design-point speed at which the turbine produces its rated power.
 * @param breakdownAngularVelocity Shaft speed at which the turbine is destroyed by overspeed. Should be well above [referenceAngularVelocity] to allow normal operation but catch runaway conditions.
 * @param breakdownTemperature Cold-side temperature at which the turbine is destroyed by overheating. Triggers when the cooling system is inadequate and the cold side runs away.
 * @param overcapacityThreshold Ratio at which the output tank triggers an explosion. If the total output fluid (water + steam) exceeds `maxCapacity * [overcapacityThreshold]`, the turbine explodes.
 */
data class SteamTurbineGeneratorModel(
    val etaFactor: Double,
    val maxFlowRate: Double,
    val maxTorque: Quantity<Torque>,
    val maxPower: Quantity<Power>,
    val coldSideMass: Quantity<Mass>,
    val coldSideMaterial: Material,
    val coldSideLeakage: ConnectionParameters,
    val referenceAngularVelocity: Quantity<AngularVelocity>,
    val breakdownAngularVelocity: Quantity<AngularVelocity>,
    val breakdownTemperature: Quantity<Temperature>,
    val overcapacityThreshold: Double,
)

class SteamTurbineKineticObject(cell: SteamTurbineCell) : KineticObject<SteamTurbineCell>(cell), PersistentObject {
    val node = KineticDouble()

    init {
        cell.shaftFriction.applyTo(node)
    }

    override fun addNodes(builder: KineticNodeSet) {
        builder.add(node)
    }

    override fun offerExtension(remote: KineticObject<*>): KineticExtension? {
        if (remote !is SteamTurbineKineticPortKineticObject) {
            return null
        }

        val localPos = cell.locator.requireLocator(Locators.BLOCK)
        val remotePos = remote.cell.locator.requireLocator(Locators.BLOCK)
        val facing = cell.locator.requireLocator(Locators.CONVENTIONAL_FACING)

        val localRemote = MultiblockTransformations.transformWorldMultiblock(
            facing.direction, localPos, remotePos
        )

        return if (localRemote.x < 0) {
            node.e1
        } else {
            node.e2
        }
    }

    override fun saveObjectNbt() = CompoundTag().also {
        it.putDouble(ANGLE, node.angle)
        it.putDouble(OMEGA, node.angularVelocity)
    }

    override fun loadObjectNbt(tag: CompoundTag) {
        node.setExternalAngle(tag.getDouble(ANGLE))
        node.angularVelocity = tag.getDouble(OMEGA)
    }

    companion object {
        private const val ANGLE = "angle"
        private const val OMEGA = "omega"
    }
}
class SteamTurbineCell(ci: CellCreateInfo, val model: SteamTurbineGeneratorModel, val shaftFriction: FrictionNodeDescription) : Cell(ci) {

    @SimObject
    val kinetic = SteamTurbineKineticObject(this)

    @SimObject
    val thermal = ThermalWireObject(
        this,
        ThermalMassDefinition(model.coldSideMaterial, mass = model.coldSideMass)(),
        model.coldSideLeakage
    )

    @Behavior
    val kineticBreakdown = KineticBreakdownBehavior.create(
        model.breakdownAngularVelocity,
        this,
        kinetic.node::angularVelocity
    )

    @Behavior
    val thermalBreakdown = ThermalBreakdownBehavior.create(
        model.breakdownTemperature,
        this,
        thermal.thermalBody::temperature
    )

    @Replicator
    fun kineticReplicator(target: InternalKineticStateConsumer) = InternalKineticReplicatorBehavior(
        RotatingKineticState.accessor(kinetic.node),
        target,
        this,
        kinetic.node::simulation
    )

    override fun kineticObjectPredicate(remote: KineticObject<*>): Boolean {
        if (remote.cell is SteamTurbineKineticPortCell) {
            return true
        }

        return super.kineticObjectPredicate(remote)
    }

    override fun thermalObjectPredicate(remote: ThermalObject<*>): Boolean {
        if (remote.cell is SteamTurbineThermalPortCell) {
            return true
        }

        return super.thermalObjectPredicate(remote)
    }
    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::simulationPre)
        subscribers.addPost(this::simulationPost)
    }

    override fun subscribeServerThread(subscribers: SubscriberCollection<ServerPhase>) {
        subscribers.addStart(this::serverStart)
        subscribers.addEnd(this::serverEnd)
    }

    //#region Snapshot and Delta

    var snapshotInputSteam = 0.0
        private set

    var snapshotInputTemp = 0.0
        private set

    var snapshotExhaustTemp = 0.0
        private set

    var deltaSteamConsumed = 0.0
        internal set

    var deltaWaterProduced = 0.0
        internal set

    var deltaSteamProduced = 0.0
        internal set

    internal var lastSentSteamFlow = Double.NaN

    var deltaColdSideHeat = 0.0
        internal set

    fun fluidHandler(): SteamTurbineBlockEntity.SteamTurbineFluidHandler? {
        return (container as? SteamTurbineBlockEntity)?.fluidHandler
    }

    //#endregion

    private val generator = SteamTurbineGenerator(this)

    private fun simulationPre(dt: Double, phase: SimulationPhase) {
        generator.preTick(dt)
    }

    private fun simulationPost(dt: Double, phase: SimulationPhase) {
        generator.postTick(dt)
    }

    private fun serverStart(dt: Double, phase: ServerPhase) {
        val handler = fluidHandler()
            ?: return

        snapshotInputSteam = handler.inputSteam
        snapshotInputTemp = handler.inputTemperature
        snapshotExhaustTemp = !thermal.thermalBody.temperature

        deltaSteamConsumed = 0.0
        deltaWaterProduced = 0.0
        deltaSteamProduced = 0.0
        deltaColdSideHeat = 0.0
    }

    private fun serverEnd(dt: Double, phase: ServerPhase) {
        val handler = fluidHandler()
            ?: return

        val consumedEnergy = deltaSteamConsumed * handler.steamCp * handler.inputTemperature
        handler.inputSteam = (handler.inputSteam - deltaSteamConsumed).coerceAtLeast(0.0)
        handler.inputFluidEnergy = (handler.inputFluidEnergy - consumedEnergy).coerceAtLeast(0.0)
        val tCold = !thermal.thermalBody.temperature

        if (deltaWaterProduced > 0.0) {
            handler.outputWater += deltaWaterProduced
            handler.outputFluidEnergy += deltaWaterProduced * handler.waterCp * tCold
        }

        if (deltaSteamProduced > 0.0) {
            handler.outputSteam += deltaSteamProduced
            handler.outputFluidEnergy += deltaSteamProduced * handler.steamCp * tCold
        }

        val totalOutput = handler.outputWater + handler.outputSteam
        val maxCapacity = SteamTurbineBlockEntity.SteamTurbineFluidHandler.OUTPUT_WATER_CAPACITY + SteamTurbineBlockEntity.SteamTurbineFluidHandler.OUTPUT_STEAM_CAPACITY

        if (totalOutput > maxCapacity * model.overcapacityThreshold) {
            explode()
            return
        }

        if (deltaSteamConsumed > 0.0 || deltaWaterProduced > 0.0 || deltaSteamProduced > 0.0) {
            setChanged()
        }

        val blockEntity = container as? SteamTurbineBlockEntity

        if (blockEntity != null) {
            val currentFlow = deltaSteamConsumed / dt

            if (!currentFlow.approxEq(lastSentSteamFlow, 0.001)) {
                lastSentSteamFlow = currentFlow
                blockEntity.sendBulkPacket(SteamTurbineBlockEntity.SteamFlowPacket::serialize, SteamTurbineBlockEntity.SteamFlowPacket(currentFlow))
            }
        }
    }

    @OnServerThread
    private fun explode() {
        val blockEntity = container as? BlockEntity
            ?: return

        val level = blockEntity.level as? ServerLevel
            ?: return

        level.destroyBlock(blockEntity.blockPos, true)
    }

    override fun saveCellData() = CompoundTag()

    override fun loadCellData(tag: CompoundTag) { }
}
/**
 * Thermodynamic core for the steam turbine. Implements a simplified Rankine cycle:
 * - Steam expands from T_hot to T_cold, producing mechanical work
 * - Waste heat (including kinetic friction) is dumped into the cold-side thermal mass
 * - Exhaust fluid exits at T_cold (water if below boiling, steam otherwise)
 */
@Suppress("UnnecessaryVariable")
class SteamTurbineGenerator(val cell: SteamTurbineCell) {
    companion object {
        private const val EPSILON_OMEGA = 1e-6
    }

    /**
     * Called in SimulationPhase.Pre. Reads snapshot values, computes thermodynamic
     * conversion, applies torque to the kinetic node, and accumulates fluid/heat deltas.
     */
    fun preTick(dt: Double) {
        val snapshotSteam = cell.snapshotInputSteam
        val snapshotTemp = cell.snapshotInputTemp

        if (snapshotSteam < FractionalFluidStack.EPSILON) {
            cell.kinetic.node.externalTorque = 0.0
            return
        }

        val maxFlow = cell.model.maxFlowRate
        val mDot = min(snapshotSteam / dt, maxFlow)

        if (mDot <= 0.0) {
            cell.kinetic.node.externalTorque = 0.0
            return
        }

        val tHot = snapshotTemp
        val tCold = !cell.thermal.thermalBody.temperature

        if (tHot <= tCold || tHot <= 0.0) {
            cell.kinetic.node.externalTorque = 0.0
            return
        }

        val eta = ((1.0 - tCold / tHot) * cell.model.etaFactor).coerceIn(0.0, 1.0)

        val tBoil = 373.15
        val steamProperties = PhysicalFluidManager.getPropertiesWithFallback(Eln2ForgeFluids.STEAM.get())
        val steamExpansionFactor = steamProperties.gasExpansionFactor
        val cpSteam = !steamProperties.specificHeatCapacity / steamExpansionFactor
        val cpWater = !PhysicalFluidManager.getPropertiesWithFallback(Fluids.WATER).specificHeatCapacity / steamExpansionFactor

        val steamTransformation = FluidTransformationManager.getTransformations(Eln2ForgeFluids.STEAM.get())
        val lVap = (steamTransformation?.condensation?.enthalpy?.value ?: 2260000.0) / steamExpansionFactor

        val hSteam = cpWater * tBoil + lVap + cpSteam * (tHot - tBoil)
        val hExhaust = if (tCold < tBoil) {
            cpWater * tCold
        } else {
            cpWater * tBoil + lVap + cpSteam * (tCold - tBoil)
        }

        val dH = hSteam - hExhaust

        if (dH <= 0.0) {
            cell.kinetic.node.externalTorque = 0.0
            return
        }

        val pThermal = mDot * dH
        val pMech = min(eta * pThermal, !cell.model.maxPower)

        val omega = abs(cell.kinetic.node.angularVelocity)
        val omegaNz = max(omega, EPSILON_OMEGA)
        val torque = min(pMech / omegaNz, !cell.model.maxTorque)

        cell.kinetic.node.externalTorque = torque

        val qWaste = pThermal - pMech

        cell.deltaSteamConsumed += mDot * dt
        if (tCold < tBoil) {
            cell.deltaWaterProduced += mDot * dt / steamExpansionFactor
        } else {
            cell.deltaSteamProduced += mDot * dt
        }
        cell.deltaColdSideHeat += qWaste * dt
    }

    /**
     * Called in SimulationPhase.Post. After the kinetic solver has run,
     * reads friction heat and adds all waste heat to the cold-side thermal mass.
     */
    fun postTick(dt: Double) {
        val frictionHeat = cell.kinetic.node.deltaHeatFromFriction
        val wasteHeat = cell.deltaColdSideHeat
        val totalHeat = wasteHeat + frictionHeat

        if (totalHeat > 0.0) {
            cell.thermal.thermalBody.energy += Quantity(totalHeat, JOULE)
        }

        cell.deltaColdSideHeat = 0.0
    }
}

class SteamTurbineBlock : UprightHorizontalDirectionCellBlock<SteamTurbineCell>() {
    override fun getCellProvider() = Eln2SteamTurbine.STEAM_TURBINE_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = SteamTurbineBlockEntity(pPos, pState)

    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState?, pAdjacentState: BlockState?, pDirection: Direction?) = true

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (pLevel.isClientSide) {
            return BlockEntityTicker { _, _, _, pBlockEntity ->
                if (pBlockEntity is SteamTurbineBlockEntity) {
                    pBlockEntity.clientTick()
                }
            }
        }

        return null
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pMovedByPiston: Boolean
    ) {
        if (!pState.`is`(pNewState.block)) {
            if (!pLevel.isClientSide) {
                val blockEntity = pLevel.getBlockEntity(pPos) as? SteamTurbineBlockEntity
                blockEntity?.destroyDelegates()
            }
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston)
    }

    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        val pos = cell.locator.requireLocator(Locators.BLOCK)
        val facing = cell.locator.requireLocator(Locators.CONVENTIONAL_FACING)
        val blockEntity = level.getBlockEntity(pos) as? SteamTurbineBlockEntity ?: return

        blockEntity.delegateMap.forEachDelegateInWorld(facing.direction, pos) { delegatePos ->
            val delegate = level.getBlockEntity(delegatePos) as? MultiblockDelegateCellBlockEntity<*> ?: return@forEachDelegateInWorld
            results.add(CellAndContainerHandle.captureInScope(delegate.cell))
        }
    }
}

class SteamTurbineBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity<SteamTurbineCell>(pos, state, Eln2SteamTurbine.STEAM_TURBINE_BLOCK_ENTITY.get()),
    BigBlockRepresentativeBlockEntity<SteamTurbineBlockEntity>,
    BulkPacketHandlerBlockEntity,
    InternalKineticStateConsumer,
    ComponentDisplay
{
    override val delegateMap: MultiblockDelegateMap get() = Eln2SteamTurbine.STEAM_TURBINE_DELEGATE_MAP.value

    @ClientOnly
    class RenderState {
        var targetAngularVelocity = 0.0
        var targetSteamFlow = 0.0

        val angularVelocitySmoother = FramerateIndependentSmoother1d(0.2)
        val steamFlowSmoother = FramerateIndependentSmoother1d(0.3)

        var steamSound: SimpleLoopingBlockEntitySoundInstance<SteamTurbineBlockEntity>? = null
        var frictionSound: SimpleLoopingBlockEntitySoundInstance<SteamTurbineBlockEntity>? = null
    }

    @ClientOnly
    var renderState: RenderState? = null
        private set

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (pLevel.isClientSide) {
            renderState = RenderState()
        }
    }

    @ClientOnly
    override val clientSidePacketHandlerLazy = createClientSideHandler()

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        handler.withHandler<RotatingKineticState>(RotatingKineticState::deserialize) {
            renderState?.targetAngularVelocity = it.angularVelocity
        }

        handler.withHandler<SteamFlowPacket>(SteamFlowPacket::deserialize) {
            renderState?.targetSteamFlow = it.flow
        }
    }

    @ServerOnly
    override fun onKineticStateChanged(state: RotatingKineticState) {
        sendBulkPacket(RotatingKineticState::serialize, state)
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState ?: return

        if (state.steamSound == null) {
            state.steamSound = SimpleLoopingBlockEntitySoundInstance(this, Eln2SteamTurbine.STEAM_TURBINE_STEAM_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    state.steamFlowSmoother.update(state.targetSteamFlow)
                    it.soundInfo = SoundInfo.steamFlow(
                        state.steamFlowSmoother.value,
                        Eln2SteamTurbine.STEAM_TURBINE_MODEL.maxFlowRate
                    )
                }

                it.registerOnAudioManager()
            }
        }

        if (state.frictionSound == null) {
            state.frictionSound = SimpleLoopingBlockEntitySoundInstance(this, Eln2SteamTurbine.STEAM_TURBINE_FRICTION_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    state.angularVelocitySmoother.update(state.targetAngularVelocity)
                    it.soundInfo = SoundInfo.turbineFriction(
                        state.angularVelocitySmoother.value,
                        !Eln2SteamTurbine.STEAM_TURBINE_MODEL.referenceAngularVelocity
                    )
                }

                it.registerOnAudioManager()
            }
        }
    }

    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        if (hasCell) {
            sendBulkPacket(RotatingKineticState::serialize, RotatingKineticState(cell.kinetic.node.angle, cell.kinetic.node.angularVelocity))

            val initialFlow = cell.deltaSteamConsumed
            cell.lastSentSteamFlow = initialFlow
            sendBulkPacket(SteamFlowPacket::serialize, SteamFlowPacket(initialFlow))
        }

        return super.getUpdateTag()
    }

    data class SteamFlowPacket(val flow: Double) {
        companion object {
            fun serialize(packet: SteamFlowPacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.flow)
            }

            fun deserialize(buffer: FriendlyByteBuf) = SteamFlowPacket(
                buffer.readDouble()
            )
        }
    }

    //#region Capability

    class SteamTurbineFluidHandler(val ambientTemperature: () -> Quantity<Temperature>) {
        companion object {
            const val INPUT_STEAM_CAPACITY = 12800.0
            const val OUTPUT_WATER_CAPACITY = 128.0
            const val OUTPUT_STEAM_CAPACITY = 12800.0

            private const val INPUT_STEAM = "inputSteam"
            private const val INPUT_FLUID_ENERGY = "inputFluidEnergy"
            private const val OUTPUT_WATER = "outputWater"
            private const val OUTPUT_STEAM = "outputSteam"
            private const val OUTPUT_FLUID_ENERGY = "outputFluidEnergy"
        }

        var inputSteam = 0.0
        var inputFluidEnergy = 0.0
        var outputWater = 0.0
        var outputSteam = 0.0
        var outputFluidEnergy = 0.0

        val steamFluid: Fluid get() = Eln2ForgeFluids.STEAM.get()
        val waterFluid: Fluid get() = Fluids.WATER

        val steamCp: Double get() = !PhysicalFluidManager.getPropertiesWithFallback(steamFluid).specificHeatCapacity / PhysicalFluidManager.getPropertiesWithFallback(steamFluid).gasExpansionFactor
        val waterCp: Double get() = !PhysicalFluidManager.getPropertiesWithFallback(waterFluid).specificHeatCapacity

        val inputTemperature: Double get() = if (inputSteam < FractionalFluidStack.EPSILON) {
            !ambientTemperature()
        } else {
            inputFluidEnergy / (inputSteam * steamCp)
        }

        val outputTemperature: Double get() {
            val totalCp = outputWater * waterCp + outputSteam * steamCp

            if (totalCp < FractionalFluidStack.EPSILON) {
                return !ambientTemperature()
            }

            return outputFluidEnergy / totalCp
        }

        val inputHandler = SteamTurbineInputFluidHandler(this)
        val outputHandler = SteamTurbineOutputFluidHandler(this)

        fun saveNbt(tag: CompoundTag) {
            tag.putDouble(INPUT_STEAM, inputSteam)
            tag.putDouble(INPUT_FLUID_ENERGY, inputFluidEnergy)
            tag.putDouble(OUTPUT_WATER, outputWater)
            tag.putDouble(OUTPUT_STEAM, outputSteam)
            tag.putDouble(OUTPUT_FLUID_ENERGY, outputFluidEnergy)
        }

        fun loadNbt(tag: CompoundTag) {
            inputSteam = tag.getDouble(INPUT_STEAM)
            inputFluidEnergy = tag.getDouble(INPUT_FLUID_ENERGY)
            outputWater = tag.getDouble(OUTPUT_WATER)
            outputSteam = tag.getDouble(OUTPUT_STEAM)
            outputFluidEnergy = tag.getDouble(OUTPUT_FLUID_ENERGY)
        }
    }

    class SteamTurbineInputFluidHandler(val parent: SteamTurbineFluidHandler) : IThermalFluidHandler {
        override fun getTanks() = 1

        override fun getFractionalFluidInTank(tank: Int) = when (tank) {
            0 -> FractionalFluidStack(parent.steamFluid, parent.inputSteam)
            else -> FractionalFluidStack.EMPTY
        }

        override fun getFractionalTankCapacity(tank: Int) = when (tank) {
            0 -> SteamTurbineFluidHandler.INPUT_STEAM_CAPACITY
            else -> 0.0
        }

        override fun getFluidInTank(tank: Int): FluidStack = getFractionalFluidInTank(tank).quantized()
        override fun getTankCapacity(tank: Int): Int = getFractionalTankCapacity(tank).toInt()

        override fun isFluidValid(tank: Int, stack: FluidStack): Boolean {
            return tank == 0 && stack.fluid == parent.steamFluid
        }

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            if (resource.isEmpty) {
                return 0
            }

            val fractional = resource.fractional()
            val simulated = fillThermal(fractional, OptionalDouble.EMPTY, IFluidHandler.FluidAction.SIMULATE)
            val quantized = fractional.copyWithAmount(simulated).quantized()

            if (quantized.isEmpty) {
                return 0
            }

            if (action == IFluidHandler.FluidAction.SIMULATE) {
                return quantized.amount
            }

            fillThermal(quantized.fractional(), OptionalDouble.EMPTY, IFluidHandler.FluidAction.EXECUTE)

            return quantized.amount
        }

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            return fillThermal(resource, OptionalDouble.EMPTY, action)
        }

        override fun fillThermal(resource: FractionalFluidStack, temperature: OptionalDouble, action: IFluidHandler.FluidAction): Double {
            if (resource.isEmpty || resource.fluid != parent.steamFluid) {
                return 0.0
            }

            val space = SteamTurbineFluidHandler.INPUT_STEAM_CAPACITY - parent.inputSteam
            val accepted = resource.amount.coerceAtMost(space)

            if (accepted < FractionalFluidStack.EPSILON) {
                return 0.0
            }

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                val incomingTemp = if (temperature.isPresent) {
                    temperature.unwrap()
                } else {
                    !parent.ambientTemperature()
                }

                parent.inputSteam += accepted
                parent.inputFluidEnergy += accepted * parent.steamCp * incomingTemp
            }

            return accepted
        }

        override fun drain(resource: FluidStack?, action: IFluidHandler.FluidAction?): FluidStack = FluidStack.EMPTY
        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction?): FluidStack = FluidStack.EMPTY
        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drainThermal(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = null
        override fun drainThermal(maxDrain: Double, action: IFluidHandler.FluidAction) = null
    }

    class SteamTurbineOutputFluidHandler(val parent: SteamTurbineFluidHandler) : IThermalFluidHandler {
        companion object {
            private const val WATER_TANK = 0
            private const val STEAM_TANK = 1
        }

        override fun getTanks() = 2

        override fun getFractionalFluidInTank(tank: Int): FractionalFluidStack {
            return when (tank) {
                WATER_TANK -> FractionalFluidStack(parent.waterFluid, parent.outputWater)
                STEAM_TANK -> FractionalFluidStack(parent.steamFluid, parent.outputSteam)
                else -> FractionalFluidStack.EMPTY
            }
        }

        override fun getFractionalTankCapacity(tank: Int): Double {
            return when (tank) {
                WATER_TANK -> SteamTurbineFluidHandler.OUTPUT_WATER_CAPACITY
                STEAM_TANK -> SteamTurbineFluidHandler.OUTPUT_STEAM_CAPACITY
                else -> 0.0
            }
        }

        override fun getFluidInTank(tank: Int): FluidStack = getFractionalFluidInTank(tank).quantized()
        override fun getTankCapacity(tank: Int): Int = getFractionalTankCapacity(tank).toInt()

        override fun isFluidValid(tank: Int, stack: FluidStack) = false

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction) = 0
        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = 0.0
        override fun fillThermal(resource: FractionalFluidStack, temperature: OptionalDouble, action: IFluidHandler.FluidAction) = 0.0

        override fun drainThermal(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            if (resource.isEmpty) {
                return null
            }

            if (resource.fluid == parent.waterFluid) {
                return drainWaterThermal(resource.amount, action)
            }

            if (resource.fluid == parent.steamFluid) {
                return drainSteamThermal(resource.amount, action)
            }

            return null
        }

        override fun drainThermal(maxDrain: Double, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            if (maxDrain < FractionalFluidStack.EPSILON) {
                return null
            }

            val fromWater = drainWaterThermal(maxDrain, action)

            if (fromWater != null) {
                return fromWater
            }

            return drainSteamThermal(maxDrain, action)
        }

        private fun drainWaterThermal(amount: Double, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            val drained = amount.coerceAtMost(parent.outputWater)

            if (drained < FractionalFluidStack.EPSILON) {
                return null
            }

            val temp = parent.outputTemperature

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                parent.outputWater -= drained
                parent.outputFluidEnergy -= drained * parent.waterCp * temp
            }

            return ThermalFluidStack(FractionalFluidStack(parent.waterFluid, drained), temp)
        }

        private fun drainSteamThermal(amount: Double, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            val drained = amount.coerceAtMost(parent.outputSteam)

            if (drained < FractionalFluidStack.EPSILON) {
                return null
            }

            val temp = parent.outputTemperature

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                parent.outputSteam -= drained
                parent.outputFluidEnergy -= drained * parent.steamCp * temp
            }

            return ThermalFluidStack(FractionalFluidStack(parent.steamFluid, drained), temp)
        }

        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): FractionalFluidStack {
            return drainThermal(resource, action)?.packet ?: FractionalFluidStack.EMPTY
        }

        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction): FractionalFluidStack {
            return drainThermal(maxDrain, action)?.packet ?: FractionalFluidStack.EMPTY
        }

        override fun drain(resource: FluidStack?, action: IFluidHandler.FluidAction?): FluidStack {
            if (resource == null || action == null || resource.isEmpty) {
                return FluidStack.EMPTY
            }

            return drainThermal(resource.fractional(), action)?.packet?.quantized() ?: FluidStack.EMPTY
        }

        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction?): FluidStack {
            if (action == null || maxDrain <= 0) {
                return FluidStack.EMPTY
            }

            return drainThermal(maxDrain.toDouble(), action)?.packet?.quantized() ?: FluidStack.EMPTY
        }
    }

    val fluidHandler = SteamTurbineFluidHandler { cell.environmentData.ambientTemperature }
    val inputFluidLazy: LazyOptional<SteamTurbineInputFluidHandler> = LazyOptional.of { fluidHandler.inputHandler }
    val outputFluidLazy: LazyOptional<SteamTurbineOutputFluidHandler> = LazyOptional.of { fluidHandler.outputHandler }

    override fun invalidateCaps() {
        super.invalidateCaps()
        inputFluidLazy.invalidate()
        outputFluidLazy.invalidate()
    }

    //#endregion

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        fluidHandler.saveNbt(pTag)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        fluidHandler.loadNbt(pTag)
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Steam Turbine Representative @ $blockPos" }
        builder.debugInIDE { "Input steam: ${fluidHandler.inputSteam} mB at ${fluidHandler.inputTemperature.rounded()} K" }
        builder.debugInIDE { "Output water: ${fluidHandler.outputWater} mB, steam: ${fluidHandler.outputSteam} mB at ${fluidHandler.outputTemperature.rounded()} K" }
        builder.debugInIDE { "Cold side: ${(!cell.thermal.thermalBody.temperature).rounded()} K" }
        builder.debugInIDE { "Shaft omega: ${cell.kinetic.node.angularVelocity.rounded()} rad/s" }
        builder.quantity(cell.thermal.thermalBody.temperature)
        builder.quantity(cell.kinetic.node.angularVelocityQuantity)
    }

    override fun setDestroyed() {
        destroyDelegates()
        super.setDestroyed()
    }
}

class SteamTurbineKineticPortKineticObject(cell: SteamTurbineKineticPortCell) : KineticObject<SteamTurbineKineticPortCell>(cell), PersistentObject {
    val node = KineticDouble()

    init {
        KINETIC_PORT_FRICTION_DESCRIPTION.applyTo(node)
    }

    override fun addNodes(builder: KineticNodeSet) {
        builder.add(node)
    }

    override fun offerExtension(remote: KineticObject<*>): KineticExtension? {
        return if (remote is SteamTurbineKineticObject) {
            node.e2
        } else {
            node.e1
        }
    }

    override fun saveObjectNbt() = CompoundTag().also {
        it.putDouble(ANGLE, node.angle)
        it.putDouble(OMEGA, node.angularVelocity)
    }

    override fun loadObjectNbt(tag: CompoundTag) {
        node.setExternalAngle(tag.getDouble(ANGLE))
        node.angularVelocity = tag.getDouble(OMEGA)
    }

    companion object {
        private const val ANGLE = "angle"
        private const val OMEGA = "omega"
    }
}

class SteamTurbineKineticPortCell(
    ci: CellCreateInfo,
    override val kineticMap: PoleMap,
    override val kineticSize: KineticSize,
) : Cell(ci), SidedKineticMapped<SteamTurbineKineticPortCell>
{
    override fun kineticObjectPredicate(remote: KineticObject<*>): Boolean {
        if (remote.cell is SteamTurbineCell) {
            return true
        }

        return super.kineticObjectPredicate(remote)
    }

    @SimObject
    val kinetic = SteamTurbineKineticPortKineticObject(this)
}

class SteamTurbineKineticDelegateBlock(val portLabel: String) : MultiblockDelegateUprightHorizontalDirectionCellBlock<SteamTurbineKineticPortCell>() {
    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentState: BlockState, pDirection: Direction) = true

    override fun getCellProvider() = Eln2SteamTurbine.STEAM_TURBINE_KINETIC_DELEGATE_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = SteamTurbineKineticDelegateBlockEntity(pPos, pState).also { it.portLabel = portLabel }

    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        val blockEntity = level.getBlockEntity(cell.locator.requireLocator(Locators.BLOCK))
            as? SteamTurbineKineticDelegateBlockEntity ?: return

        val representative = getRepresentativeFromDelegateBlockEntity<SteamTurbineBlockEntity>(
            blockEntity, blockEntity.representativePos
        )

        planarCellScan(
            level,
            cell,
            cell.locator.requireLocator(Locators.CONVENTIONAL_FACING).direction.opposite,
            results::add
        )

        results.add(CellAndContainerHandle.captureInScope(representative.cell))
    }
}

class SteamTurbineKineticDelegateBlockEntity(pos: BlockPos, state: BlockState) :
    MultiblockDelegateCellBlockEntity<SteamTurbineKineticPortCell>(
        pos, state, Eln2SteamTurbine.STEAM_TURBINE_KINETIC_DELEGATE_BLOCK_ENTITY.get()
    ),
    ComponentDisplay
{
    var portLabel: String = "?"

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Kinetic Port [$portLabel] @ $blockPos (rep: $representativePos)" }
        builder.debugInIDE { "Vel: ${cell.kinetic.node.angularVelocity.rounded()}" }
    }
}

class SteamTurbineThermalPortCell(
    ci: CellCreateInfo,
    thermalDef: ThermalMassDefinition,
    override val thermalMap: MonopoleMap,
    override val thermalSize: ThermalSize,
) : Cell(ci), SidedThermalMonoMapped<SteamTurbineThermalPortCell>
{
    override fun thermalObjectPredicate(remote: ThermalObject<*>): Boolean {
        if (remote.cell is SteamTurbineCell) {
            return true
        }

        return super.thermalObjectPredicate(remote)
    }

    @SimObject
    val thermalWire = ThermalWireObject(this, thermalDef())
}

class SteamTurbineThermalDelegateBlock(val portLabel: String) :
    MultiblockDelegateUprightHorizontalDirectionCellBlock<SteamTurbineThermalPortCell>()
{
    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentState: BlockState, pDirection: Direction) = true

    override fun getCellProvider() = Eln2SteamTurbine.STEAM_TURBINE_THERMAL_DELEGATE_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) =
        SteamTurbineThermalDelegateBlockEntity(pPos, pState).also { it.portLabel = portLabel }

    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        val blockEntity = level.getBlockEntity(cell.locator.requireLocator(Locators.BLOCK))
            as? SteamTurbineThermalDelegateBlockEntity ?: return

        val representative = getRepresentativeFromDelegateBlockEntity<SteamTurbineBlockEntity>(
            blockEntity, blockEntity.representativePos
        )

        planarCellScan(
            level,
            cell,
            cell.locator.requireLocator(Locators.CONVENTIONAL_FACING).direction.opposite,
            results::add
        )

        results.add(CellAndContainerHandle.captureInScope(representative.cell))
    }
}

class SteamTurbineThermalDelegateBlockEntity(pos: BlockPos, state: BlockState) :
    MultiblockDelegateCellBlockEntity<SteamTurbineThermalPortCell>(
        pos, state, Eln2SteamTurbine.STEAM_TURBINE_THERMAL_DELEGATE_BLOCK_ENTITY.get()
    ),
    ComponentDisplay
{
    var portLabel: String = "?"

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Thermal Port [$portLabel] @ $blockPos (rep: $representativePos)" }
    }
}

class SteamTurbineFluidDelegateBlock : MultiblockDelegateBlock() {
    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) =
        SteamTurbineFluidDelegateBlockEntity(pPos, pState)
}

class SteamTurbineFluidDelegateBlockEntity(pos: BlockPos, state: BlockState) :
    MultiblockDelegateBlockEntity(
        pos, state, Eln2SteamTurbine.STEAM_TURBINE_FLUID_DELEGATE_BLOCK_ENTITY.get()
    ),
    ComponentDisplay
{
    val isInputPort: Boolean
        get() = blockState.block === Eln2SteamTurbine.STEAM_TURBINE_FLUID_DELEGATE_BLOCK_INPUT.get()

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            val representativePos = this.representativePos
                ?: return super.getCapability(cap, side)

            val level = this.level
                ?: return super.getCapability(cap, side)

            if (!level.isLoaded(representativePos)) {
                return LazyOptional.empty()
            }

            val representative = level.getBlockEntity(representativePos) as? SteamTurbineBlockEntity
                ?: return super.getCapability(cap, side)

            return if (isInputPort) {
                representative.inputFluidLazy.cast()
            } else {
                representative.outputFluidLazy.cast()
            }
        }

        return super.getCapability(cap, side)
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        val portName = if (isInputPort) "Steam Input" else "Water/Steam Output"
        builder.debugInIDE { "Fluid Port [$portName] @ $blockPos (rep: $representativePos)" }
    }
}

private val KINETIC_PORT_FRICTION_DESCRIPTION = FrictionNodeDescription(
    Quantity(0.001, KILOGRAM_METER2),
    NodeFrictionDescription(
        0.0,
        Quantity(0.0, NEWTON_METER),
        Quantity(0.0, NEWTON_METER)
    )
)

class SteamTurbineBlockEntityVisual(
    ctx: VisualizationContext,
    blockEntity: SteamTurbineBlockEntity,
    partialTick: Float
) : AbstractBlockEntityVisual<SteamTurbineBlockEntity>(ctx, blockEntity, partialTick), ShaderLightVisual {
    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(FlwModels.STEAM_TURBINE, FlwMaterials.CUTOUT_SMOOTH_LIT))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.center()
            it.rotateToFace(blockEntity.representativeFacing.opposite)
            it.uncenter()
        }

    override fun updateLight(p0: Float) {
        // NOOP
    }

    override fun setSectionCollector(sectionCollector: SectionTrackedVisual.SectionCollector) {
        this.lightSections = sectionCollector

        sectionCollector.sections(
            blockEntity.delegateMap.getTotalSpannedSectionsFat(
                blockState.getValue(HorizontalDirectionalBlock.FACING),
                pos
            )
        )
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(body)
    }

    override fun _delete() {
        body.delete()
    }
}
