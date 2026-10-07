@file:Suppress("unused")

package org.eln2.mc.common.content.fluid

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import org.ageseries.libage.data.registerHandler
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import org.ageseries.libage.mathematics.approxEq
import org.eln2.mc.ClientOnly
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.content.modules.Eln2ForgeFluids
import org.eln2.mc.common.fluids.foundation.*
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.eln2.mc.extensions.eln2StandardBlockProperties
import org.eln2.mc.extensions.plus
import org.eln2.mc.extensions.rotatedAround
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.joml.Quaternionf
import org.joml.Vector3f

class GasReleaseBlock : Block(eln2StandardBlockProperties().noOcclusion()), EntityBlock {
    init {
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.DOWN))
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(FACING)
    }

    /**
     * The facing stores the world-space direction the connector (model DOWN face) points to, which is into the surface the block is placed on.
     * */
    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(FACING, pContext.clickedFace.opposite)
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity = GasReleaseBlockEntity(pPos, pState)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun neighborChanged(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pBlock: Block,
        pFromPos: BlockPos,
        pIsMoving: Boolean,
    ) {
        @Suppress("DEPRECATION")
        super.neighborChanged(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving)

        if (!pLevel.isClientSide) {
            (pLevel.getBlockEntity(pPos) as? GasReleaseBlockEntity)?.markNeighborChanged()
        }
    }

    override fun onNeighborChange(pState: BlockState, pLevel: LevelReader, pPos: BlockPos, pNeighborPos: BlockPos) {
        super.onNeighborChange(pState, pLevel, pPos, pNeighborPos)

        if (pLevel is Level && !pLevel.isClientSide) {
            (pLevel.getBlockEntity(pPos) as? GasReleaseBlockEntity)?.markNeighborChanged()
        }
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (pLevel.isClientSide) {
            return BlockEntityTicker { _, _, _, blockEntity ->
                if (blockEntity is GasReleaseBlockEntity) {
                    blockEntity.clientTick()
                }
            }
        }

        return BlockEntityTicker { _, _, _, blockEntity ->
            if (blockEntity is GasReleaseBlockEntity) {
                blockEntity.serverTick()
            }
        }
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        val blockEntity = pLevel.getBlockEntity(pPos) as? GasReleaseBlockEntity
            ?: return

        val activity = blockEntity.renderState?.activitySmoother?.value ?: return

        if (activity < 0.01) {
            return
        }

        val facing = pState.getValue(FACING)
        val centerX = pPos.x + 0.5
        val centerY = pPos.y + 0.5
        val centerZ = pPos.z + 0.5

        val dx = facing.stepX.toDouble() * 0.6
        val dy = facing.stepY.toDouble() * 0.6
        val dz = facing.stepZ.toDouble() * 0.6

        if (pRandom.nextDouble() < 0.3 * activity) {
            pLevel.addParticle(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                centerX + dx, centerY + dy, centerZ + dz,
                dx * 0.02, dy * 0.02 + 0.04, dz * 0.02
            )
        }

        if (pRandom.nextDouble() < 0.15 * activity) {
            pLevel.addParticle(
                ParticleTypes.SMOKE,
                centerX + dx + (pRandom.nextDouble() - 0.5) * 0.3,
                centerY + dy + (pRandom.nextDouble() - 0.5) * 0.3,
                centerZ + dz + (pRandom.nextDouble() - 0.5) * 0.3,
                0.0, 0.02, 0.0
            )
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = COLLIDERS[pState.getValue(FACING).get3DDataValue()]

    companion object {
        val FACING: DirectionProperty = BlockStateProperties.FACING

        private val MODEL_BOX = AABB(6.0 / 16.0, 0.0, 6.0 / 16.0, 10.0 / 16.0, 2.0 / 16.0, 10.0 / 16.0)

        private val COLLIDERS: Array<VoxelShape> = Array(6) { dataValue ->
            val facing = Direction.from3DDataValue(dataValue)
            val rotation = Quaternionf().rotationTo(
                Vector3f(0.0f, -1.0f, 0.0f),
                Vector3f(facing.stepX.toFloat(), facing.stepY.toFloat(), facing.stepZ.toFloat())
            )
            Shapes.create(MODEL_BOX.rotatedAround( Vector3f(0.5f, 0.5f, 0.5f), rotation))
        }
    }
}

class GasReleaseBlockEntity(pPos: BlockPos, pState: BlockState) :
    BlockEntity(Eln2ForgeFluids.GAS_RELEASE_BLOCK_ENTITY.get(), pPos, pState),
    BulkPacketHandlerBlockEntity,
    ComponentDisplay {

    companion object {
        /**
         * Flow rate (mB/tick) at which the vent is considered fully active for A/V purposes.
         * */
        private const val NOMINAL_FLOW = 10.0

        private fun isGaseous(fluid: Fluid): Boolean = PhysicalFluidManager.getProperties(fluid)?.isGaseous == true
    }

    /**
     * Accepts and voids any gaseous fluid offered to its connector face.
     * Liquids and unregistered fluids are rejected.
     * */
    class GasVoidHandler : IFractionalFluidHandler {
        /**
         * mB voided via fill calls (pushed by pipes) since the last [serverTick] reset.
         * */
        var voidedAmount = 0.0

        override fun getTanks(): Int = 1

        override fun getFluidInTank(tank: Int): FluidStack = FluidStack.EMPTY

        override fun getFractionalFluidInTank(tank: Int): FractionalFluidStack = FractionalFluidStack.EMPTY

        /**
         * Reports an effectively unbounded capacity so connected pipes always attempt to push gas into the vent.
         * */
        override fun getTankCapacity(tank: Int): Int = NOMINAL_FLOW.toInt()

        override fun getFractionalTankCapacity(tank: Int): Double = NOMINAL_FLOW

        override fun isFluidValid(tank: Int, stack: FluidStack): Boolean = isGaseous(stack.fluid)

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            if (resource.isEmpty || !isGaseous(resource.fluid)) {
                return 0.0
            }

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                voidedAmount += resource.amount
            }

            return resource.amount
        }

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            if (resource.isEmpty || !isGaseous(resource.fluid)) {
                return 0
            }

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                voidedAmount += resource.amount.toDouble()
            }

            return resource.amount
        }

        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY

        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY

        override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY

        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY
    }

    private val handler = GasVoidHandler()
    private val handlerLazy: LazyOptional<GasVoidHandler> = LazyOptional.of { handler }

    /**
     * Set when a neighbor changes or when the block entity loads on the server, so the next [serverTick] re-resolves the neighbor's fluid handler on the connector face.
     * */
    @ServerOnly
    private var neighborScanPending = false

    /**
     * The resolved fluid handler on the connector face, or null if none is present.
     * */
    @ServerOnly
    private var neighborHandler: LazyOptional<IFluidHandler>? = null

    /**
     * Total mB voided (pushed + pulled) during the current server tick, used to drive A/V.
     * */
    @ServerOnly
    private var voidedThisTick = 0.0

    @ServerOnly
    private var lastSentActivity = Double.NaN

    @ServerOnly
    fun markNeighborChanged() {
        neighborScanPending = true
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (pLevel.isClientSide) {
            renderState = RenderState()
        } else {
            neighborScanPending = true
        }
    }

    //#region Client State

    @ClientOnly
    class RenderState {
        var targetActivity = 0.0
        val activitySmoother = FramerateIndependentSmoother1d(0.3)
        var soundInstance: SimpleLoopingBlockEntitySoundInstance<GasReleaseBlockEntity>? = null
    }

    @ClientOnly
    var renderState: RenderState? = null
        private set

    override val clientSidePacketHandlerLazy = createClientSideHandler()

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        handler.withHandler<VentActivityPacket>(VentActivityPacket::deserialize) {
            renderState?.targetActivity = it.activity
        }
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState
            ?: return

        state.activitySmoother.update(state.targetActivity)

        if (state.soundInstance == null) {
            state.soundInstance = SimpleLoopingBlockEntitySoundInstance(this, Eln2ForgeFluids.GAS_RELEASE_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    it.soundInfo = SoundInfo.steamFlow(state.activitySmoother.value, 1.0)
                }

                it.registerOnAudioManager()
            }
        }
    }

    //#endregion

    //#region Server Tick

    @ServerOnly
    fun serverTick() {
        voidedThisTick = handler.voidedAmount
        handler.voidedAmount = 0.0

        if (neighborScanPending) {
            neighborScanPending = false
            resolveNeighborHandler()
        }

        drainNeighborGas()

        var activity = (voidedThisTick * 1.0 / NOMINAL_FLOW).coerceIn(0.0, 1.0)

        val epsilon = 0.01

        if(activity < epsilon) {
            activity = 0.0
        }

        if (!activity.approxEq(lastSentActivity, epsilon)) {
            lastSentActivity = activity
            sendBulkPacket(VentActivityPacket::serialize, VentActivityPacket(activity))
        }
    }

    /**
     * Resolves the fluid handler on the block adjacent to the connector face.
     * Attaches an invalidation listener so a neighbor capability change triggers a re-scan.
     * */
    @ServerOnly
    private fun resolveNeighborHandler() {
        neighborHandler = null

        val level = level ?: return
        if (level.isClientSide) {
            return
        }

        val connectorDir = blockState.getValue(GasReleaseBlock.FACING)
        val neighborPos = blockPos + connectorDir

        if (!level.isLoaded(neighborPos)) {
            return
        }

        val neighborEntity = level.getBlockEntity(neighborPos) ?: return
        val lazy = neighborEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, connectorDir.opposite)

        if (!lazy.isPresent) {
            return
        }

        neighborHandler = lazy
        lazy.addListener { neighborScanPending = true }
    }

    /**
     * Drains all gaseous fluids from the neighbor handler, voiding the result.
     * Thermal handlers are drained via [IThermalFluidHandler.drainThermal] to avoid corrupting the source's thermal state.
     * */
    @ServerOnly
    private fun drainNeighborGas() {
        val lazy = neighborHandler ?: return

        if (!lazy.isPresent) {
            neighborScanPending = true
            return
        }

        val handler = lazy.resolve().orElse(null) ?: return

        for (tank in 0 until handler.tanks) {
            val fractional = if (handler is IFractionalFluidHandler) {
                handler.getFractionalFluidInTank(tank)
            } else {
                handler.getFluidInTank(tank).fractional()
            }

            if (fractional.isEmpty || !isGaseous(fractional.fluid)) {
                continue
            }

            val drainedAmount = if (handler is IThermalFluidHandler) {
                handler.drainThermal(fractional, IFluidHandler.FluidAction.EXECUTE)?.packet?.amount ?: 0.0
            } else if (handler is IFractionalFluidHandler) {
                handler.drainFractional(fractional, IFluidHandler.FluidAction.EXECUTE).amount
            } else {
                val discrete = handler.getFluidInTank(tank)

                if (discrete.isEmpty) {
                    0.0
                } else {
                    handler.drain(discrete, IFluidHandler.FluidAction.EXECUTE).amount.toDouble()
                }
            }

            voidedThisTick += drainedAmount
        }
    }

    //#endregion

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.FLUID_HANDLER && side == blockState.getValue(GasReleaseBlock.FACING)) {
            return handlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        handlerLazy.invalidate()
        neighborHandler = null
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Gas vent: connector on ${blockState.getValue(GasReleaseBlock.FACING)}" }
    }

    data class VentActivityPacket(val activity: Double) {
        companion object {
            fun serialize(packet: VentActivityPacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.activity)
            }

            fun deserialize(buffer: FriendlyByteBuf) = VentActivityPacket(
                buffer.readDouble()
            )
        }
    }
}
