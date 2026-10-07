package org.eln2.mc.common.content.processing

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.PushReaction
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.content.modules.Eln2ForgeFluids
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.fluids.foundation.FractionalFluidStack
import org.eln2.mc.common.fluids.foundation.IFractionalFluidHandler
import org.eln2.mc.common.fluids.foundation.fractional
import org.eln2.mc.extensions.eln2StandardBlockProperties
import org.eln2.mc.extensions.plus
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.requireIsOnServerThread
import kotlin.math.min

@ServerOnly
object LeadChamberExecutionManager {
    private class LevelData {
        val blockEntities = HashMap<LeadChamberBlockEntity, LeadChamberBlockEntity.LeadChamberSimulation>()
        val simulationList = ArrayList<LeadChamberBlockEntity.LeadChamberSimulation>()
    }

    private val levels = HashMap<ServerLevel, LevelData>()

    private fun getDataForLevel(level: Level) : LevelData {
        requireIsOnServerThread()

        val serverLevel = level as? ServerLevel
            ?: error("The lead chamber's level isn't a ServerLevel")

        return levels.computeIfAbsent(serverLevel) {
            LevelData()
        }
    }

    /**
     * Registers a lead chamber for execution.
     * Must be called only once for the lifecycle of the given block entity.
     * */
    fun registerBlockEntity(blockEntity: LeadChamberBlockEntity) {
        requireIsOnServerThread()

        val data = getDataForLevel(blockEntity.level!!)

        data.blockEntities.putUnique(blockEntity, blockEntity.simulation) {
            "Duplicate add lead chamber ${blockEntity.blockPos}"
        }

        data.simulationList.add(blockEntity.simulation)
    }

    /**
     * Unregisters a lead chamber. Called when its chunk is unloaded, or when it gets destroyed.
     * Allows multiple calls.
     * */
    fun unregisterBlockEntity(blockEntity: LeadChamberBlockEntity) {
        requireIsOnServerThread()

        val data = getDataForLevel(blockEntity.level!!)

        if(data.blockEntities.remove(blockEntity) != null) {
            data.simulationList.remove(blockEntity.simulation)
        }
    }

    /**
     * Called on the server thread by [org.eln2.mc.common.ForgeEvents.onServerTick].
     * */
    fun dispatch() {
        levels.values.forEach {
            dispatch(it)
        }
    }

    /**
     * Executes the three passes of the simulation. Also removes stale neighbor references from [LeadChamberBlockEntity.linkedCells].
     * */
    private fun dispatch(data: LevelData) {
        val simulationList = data.simulationList
        val count = simulationList.size

        for (i in 0 until count) {
            val simulation = simulationList[i]
            val blockEntity = simulation.blockEntity

            blockEntity.rebuildLinksIfRequired()

            val level = blockEntity.level!!
            for (i in 0 until 6) {
                val neighbor = blockEntity.linkedCells[i]
                    ?: continue

                // TODO Optimization: bake unique chunk positions
                if (!level.isLoaded(neighbor.blockPos)) {
                    blockEntity.linkedCells[i] = null
                }
            }

            simulation.transformationReaction()
            simulation.proposalPass()
        }

        for (i in 0 until count) {
            simulationList[i].transferPass()
        }

        for (i in 0 until count) {
            simulationList[i].setChangedIfRequired()
        }
    }
}

class LeadChamberBlock : Block(eln2StandardBlockProperties().noOcclusion().pushReaction(PushReaction.BLOCK)), EntityBlock {
    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return LeadChamberBlockEntity(pPos, pState)
    }

    private fun getBlockEntity(level: Level, pos: BlockPos): LeadChamberBlockEntity? {
        return level.getBlockEntity(pos) as? LeadChamberBlockEntity
    }

    @Deprecated("Deprecated in Java")
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
            getBlockEntity(pLevel, pPos)?.markForRebuild()
        }
    }

    override fun onNeighborChange(pState: BlockState, pLevel: LevelReader, pPos: BlockPos, pNeighborPos: BlockPos) {
        super.onNeighborChange(pState, pLevel, pPos, pNeighborPos)

        if (pLevel is Level && !pLevel.isClientSide) {
            getBlockEntity(pLevel, pPos)?.markForRebuild()
        }
    }
}

class LeadChamberBlockEntity(pPos: BlockPos, pState: BlockState) : BlockEntity(Eln2Processing.LEAD_CHAMBER_BLOCK_ENTITY.get(), pPos, pState), ComponentDisplay {
    companion object {
        private const val SULFURIC_ACID = "sulfuric_acid"
        private const val SULFUR_DIOXIDE = "sulfur_dioxide"
        private const val STEAM = "steam"
        private const val NITROGEN_DIOXIDE = "nitrogen_dioxide"
    }

    /**
     * Set to true when a neighbor change is detected (via [LeadChamberBlock.neighborChanged] or [LeadChamberBlock.onNeighborChange]).
     */
    @ServerOnly
    var markedForRebuildLinks = false

    fun markForRebuild() {
        markedForRebuildLinks = true
    }

    /**
     * The neighbor cells, indexed by [Direction.get3DDataValue].
     * */
    @ServerOnly
    var linkedCells = Array<LeadChamberBlockEntity?>(6) { null }

    //#region Capability

    /**
     * Fluid handler for the chamber block. Handles dilute sulfuric acid, sulfur dioxide, steam, and nitrogen dioxide.
     * Allows insertion of all, and extraction of dilute sulfuric acid.
     * */
    class FluidHandler(val capacity: Double) : IFractionalFluidHandler {
        companion object {
            private const val SULFURIC_ACID_INDEX = 0
            private const val SULFUR_DIOXIDE_INDEX = 1
            private const val STEAM_INDEX = 2
            private const val NITROGEN_DIOXIDE_INDEX = 3

            private val sulfuricAcidFluid: Fluid get() = Eln2ForgeFluids.DILUTE_SULFURIC_ACID.get()
            private val sulfurDioxideFluid: Fluid get() = Eln2ForgeFluids.SULFUR_DIOXIDE.get()
            private val steamFluid: Fluid get() = Eln2ForgeFluids.STEAM.get()
            private val nitrogenDioxideFluid: Fluid get() = Eln2ForgeFluids.NITROGEN_DIOXIDE.get()
        }

        /**
         * mB of dilute sulfuric acid in the tank.
         * */
        var sulfuricAcid = 0.0

        /**
         * mB of sulfur dioxide in the tank.
         * */
        var sulfurDioxide = 0.0

        /**
         * mB of steam in the tank.
         * */
        var steam = 0.0

        /**
         * mB of nitrogen dioxide in the tank.
         * */
        var nitrogenDioxide = 0.0

        /**
         * Total mB in the tank (less than or equal to [capacity]).
         * */
        val totalAmount: Double get() = sulfuricAcid + sulfurDioxide + steam + nitrogenDioxide

        /**
         * Remaining free mB in the tank.
         * */
        val remainingCapacity: Double get() = (capacity - totalAmount).coerceAtLeast(0.0)

        override fun getFractionalFluidInTank(tank: Int) = when (tank) {
            SULFURIC_ACID_INDEX -> FractionalFluidStack(sulfuricAcidFluid, sulfuricAcid)
            SULFUR_DIOXIDE_INDEX -> FractionalFluidStack(sulfurDioxideFluid, sulfurDioxide)
            STEAM_INDEX -> FractionalFluidStack(steamFluid, steam)
            NITROGEN_DIOXIDE_INDEX -> FractionalFluidStack(nitrogenDioxideFluid, nitrogenDioxide)
            else -> FractionalFluidStack.EMPTY
        }

        override fun getFractionalTankCapacity(tank: Int) = capacity
        override fun getTanks(): Int = 4
        override fun getFluidInTank(tank: Int): FluidStack = getFractionalFluidInTank(tank).quantized()
        override fun getTankCapacity(tank: Int): Int = capacity.toInt()

        override fun isFluidValid(tank: Int, stack: FluidStack): Boolean = when (tank) {
            SULFURIC_ACID_INDEX -> stack.fluid == sulfuricAcidFluid
            SULFUR_DIOXIDE_INDEX -> stack.fluid == sulfurDioxideFluid
            STEAM_INDEX -> stack.fluid == steamFluid
            NITROGEN_DIOXIDE_INDEX -> stack.fluid == nitrogenDioxideFluid
            else -> false
        }

        /**
         * Only accepts sulfur dioxide, steam, and NO2.
         * Rejects sulfuric acid; it is only produced internally by the simulation.
         */
        //#region Filling

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            if (resource.isEmpty) {
                return 0.0
            }

            val fluid = resource.fluid

            if (fluid == sulfuricAcidFluid) {
                return 0.0
            }

            val accepted = resource.amount.coerceAtMost(remainingCapacity)

            if (accepted < FractionalFluidStack.EPSILON) {
                return 0.0
            }

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                when (fluid) {
                    sulfurDioxideFluid -> sulfurDioxide += accepted
                    steamFluid -> steam += accepted
                    nitrogenDioxideFluid -> nitrogenDioxide += accepted
                    else -> return 0.0
                }
            }

            return accepted
        }

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            if (resource.isEmpty) {
                return 0
            }

            val fractional = resource.fractional()
            val simulated = fillFractional(fractional, IFluidHandler.FluidAction.SIMULATE)
            val quantized = fractional.copyWithAmount(simulated).quantized()

            if (quantized.isEmpty) {
                return 0
            }

            if (action == IFluidHandler.FluidAction.SIMULATE) {
                return quantized.amount
            }

            fillFractional(quantized.fractional(), IFluidHandler.FluidAction.EXECUTE)
            return quantized.amount
        }

        //#endregion

        /**
         * Only allows draining sulfuric acid.
         * Gases and catalyst stay in the chamber.
         */
        //#region Draining

        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): FractionalFluidStack {
            if (resource.isEmpty) {
                return FractionalFluidStack.EMPTY
            }

            if (resource.fluid != sulfuricAcidFluid) {
                return FractionalFluidStack.EMPTY
            }

            val drained = resource.amount.coerceAtMost(sulfuricAcid)

            if (drained < FractionalFluidStack.EPSILON) {
                return FractionalFluidStack.EMPTY
            }

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                sulfuricAcid -= drained
            }

            return FractionalFluidStack(sulfuricAcidFluid, drained)
        }

        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction): FractionalFluidStack {
            if (maxDrain < FractionalFluidStack.EPSILON || sulfuricAcid < FractionalFluidStack.EPSILON) {
                return FractionalFluidStack.EMPTY
            }

            val drained = maxDrain.coerceAtMost(sulfuricAcid)

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                sulfuricAcid -= drained
            }

            return FractionalFluidStack(sulfuricAcidFluid, drained)
        }

        override fun drain(resource: FluidStack?, action: IFluidHandler.FluidAction?): FluidStack {
            if (resource == null || action == null) {
                return FluidStack.EMPTY
            }

            return drainFractional(resource.fractional(), action).quantized()
        }

        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction?): FluidStack {
            if (action == null) {
                return FluidStack.EMPTY
            }

            return drainFractional(maxDrain.toDouble(), action).quantized()
        }

        //#endregion
    }

    val tank = FluidHandler(40000.0)
    val tankLazy: LazyOptional<FluidHandler> = LazyOptional.of { tank }

    /**
     * Wrapper for [FluidHandler] that prevents extraction.
     * */
    class InsertOnlyHandler(parent: FluidHandler) : IFractionalFluidHandler by parent {
        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drain(resource: FluidStack?, action: IFluidHandler.FluidAction?): FluidStack = FluidStack.EMPTY
        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction?): FluidStack = FluidStack.EMPTY
    }

    val insertOnlyTank = InsertOnlyHandler(tank)
    val insertOnlyTankLazy: LazyOptional<InsertOnlyHandler> = LazyOptional.of { insertOnlyTank }

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T?> {
        if(cap == ForgeCapabilities.FLUID_HANDLER) {
            if(side != null) {
                return when(side) {
                    Direction.DOWN -> tankLazy.cast()
                    else -> insertOnlyTankLazy.cast()
                }
            }
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        tankLazy.invalidate()
        insertOnlyTankLazy.invalidate()
    }

    //#endregion

    @ServerOnly
    class LeadChamberSimulation(val blockEntity: LeadChamberBlockEntity) {
        companion object {
            /**
             * Base reaction rate (mB/tick). Zero means no reaction will occur without nitrogen dioxide.
             * */
            const val BASE_REACTION_RATE_MB_PER_TICK = 0.0

            /**
             * Max reaction rate at full catalyst saturation (mB/tick).
             * Calculated as: 1000 mB acid / 15-min cycle / 27-block chamber ≈ 0.00206 mB/tick/block.
             */
            const val MAX_REACTION_RATE_MB_PER_TICK = 1000.0 / (15.0 * 60.0 * 20.0 * 27.0)

            /**
             * Stoichiometric ratios: mB consumed per mB acid produced.
             * */
            const val STEAM_PER_ACID = 100.0
            const val SULFUR_DIOXIDE_PER_ACID = 100.0

            /**
             * Nitrogen dioxide beyond this fraction of the capacity will not increase the reaction rate.
             * This is done to prevent the player spamming catalyst.
             * */
            const val MAX_CATALYST_FRACTION = 0.05
            
            /**
             * Dampening for diffusion equalization. Based on the 6 neighbors for stability.
             */
            private const val DAMPENING = 1.0 / 6.0

            /**
             * Max total liquid outflow (mB/tick).
             * */
            private const val MAX_LIQUID_FLOW_RATE = 10.0

            /**
             * Max total gas outflow per fluid species (mB/tick).
             * */
            private const val MAX_GAS_FLOW_RATE = 1500.0

            private val HORIZONTAL_DIRECTIONS = intArrayOf(
                Direction.NORTH.get3DDataValue(),
                Direction.SOUTH.get3DDataValue(),
                Direction.EAST.get3DDataValue(),
                Direction.WEST.get3DDataValue()
            )

            /**
             * Opposite direction lookup.
             * Used by [transferPass] to read neighbor buffers aimed at the cell.
             * */
            private val OPPOSITE_DIRS = intArrayOf(
                Direction.DOWN.opposite.get3DDataValue(),
                Direction.UP.opposite.get3DDataValue(),
                Direction.NORTH.opposite.get3DDataValue(),
                Direction.SOUTH.opposite.get3DDataValue(),
                Direction.WEST.opposite.get3DDataValue(),
                Direction.EAST.opposite.get3DDataValue(),
            )
        }

        /**
         * Converts sulfur dioxide and water to dilute sulfuric acid, with the rate dictated by the concentration of the nitrogen dioxide catalyst.
         */
        fun transformationReaction() {
            val tank = blockEntity.tank

            val epsilon = FractionalFluidStack.EPSILON

            if (tank.steam < epsilon || tank.sulfurDioxide < epsilon) {
                return
            }

            val catalystFraction = tank.nitrogenDioxide / tank.capacity
            val effectiveFraction = min(catalystFraction, MAX_CATALYST_FRACTION)
            val catalystRatio = effectiveFraction / MAX_CATALYST_FRACTION
            
            val reactionRate = BASE_REACTION_RATE_MB_PER_TICK + (MAX_REACTION_RATE_MB_PER_TICK - BASE_REACTION_RATE_MB_PER_TICK) * catalystRatio

            var possibleAcid = min(
                reactionRate,
                min(tank.steam / STEAM_PER_ACID, tank.sulfurDioxide / SULFUR_DIOXIDE_PER_ACID)
            )

            if (possibleAcid < epsilon) {
                return
            }

            val volumeIncreasePerAcid = 1.0 - STEAM_PER_ACID - SULFUR_DIOXIDE_PER_ACID

            @Suppress("KotlinConstantConditions")
            if (volumeIncreasePerAcid > 0.0) {
                possibleAcid = minOf(possibleAcid, tank.remainingCapacity / volumeIncreasePerAcid)
            }

            if (possibleAcid >= epsilon) {
                tank.steam -= possibleAcid * STEAM_PER_ACID
                tank.sulfurDioxide -= possibleAcid * SULFUR_DIOXIDE_PER_ACID
                tank.sulfuricAcid += possibleAcid
            }
        }

        /**
         * Compact variant of [PhaseChangeModuleCell.PhaseChangeSimulation.TransferBuffer].
         * Written by [proposalPass] (proposals), read by neighbor [transferPass].
         */
        @Suppress("NOTHING_TO_INLINE")
        @JvmInline
        private value class TransferBuffers(val data: DoubleArray) {
            inline fun getSulfuricAcid(index: Int) = data[index * 4 + 0]
            inline fun setSulfuricAcid(index: Int, value: Double) { data[index * 4 + 0] = value }

            inline fun getSulfurDioxide(index: Int) = data[index * 4 + 1]
            inline fun setSulfurDioxide(index: Int, value: Double) { data[index * 4 + 1] = value }

            inline fun getSteam(index: Int) = data[index * 4 + 2]
            inline fun setSteam(index: Int, value: Double) { data[index * 4 + 2] = value }

            inline fun getNitrogenDioxide(index: Int) = data[index * 4 + 3]
            inline fun setNitrogenDioxide(index: Int, value: Double) { data[index * 4 + 3] = value }

            inline fun clear() = data.fill(0.0)
        }

        private val outboundBuffers = TransferBuffers(DoubleArray(24))
        private var hasTransferred = false

        private val tempSulfurDioxide = DoubleArray(6)
        private val tempSteam = DoubleArray(6)
        private val tempNitrogenDioxide = DoubleArray(6)
        private val tempSulfuricAcid = DoubleArray(6)

        /**
         * Pass 1: Computes proposed outbound transfers into [outboundBuffers].
         *
         * Gas diffusion is isotropic via equalization: `transfer = DAMPENING * (local - neighbor)`
         * Unlike the [PhaseChangeModuleCell.PhaseChangeSimulation], gases circulate freely to fill the chamber uniformly.
         *
         * Liquid acid prefers falling as much as the down-neighbor's remaining capacity allows.
         * Any acid that cannot fall spreads horizontally via equalization.
         *
         * After computing proposals, each fluid's total is clamped to the per-species flow-rate cap and the available amount (source-side scaling).
         * This is required because [transferPass] is receiver-centric and reads our buffers without further source checks.
         */
        @Suppress("JoinDeclarationAndAssignment")
        fun proposalPass() {
            val tank = blockEntity.tank
            val linkedCells = blockEntity.linkedCells
            val outboundBuffers = outboundBuffers

            outboundBuffers.clear()

            val epsilon = FractionalFluidStack.EPSILON

            if (tank.totalAmount < epsilon) {
                return
            }

            val tempSulfurDioxide = tempSulfurDioxide
            val tempSteam = tempSteam
            val tempNitrogenDioxide = tempNitrogenDioxide
            val tempSulfuricAcid = tempSulfuricAcid

            tempSulfurDioxide.fill(0.0)
            tempSteam.fill(0.0)
            tempNitrogenDioxide.fill(0.0)
            tempSulfuricAcid.fill(0.0)

            var sumSulfurDioxide = 0.0
            var sumSteam = 0.0
            var sumNitrogenDioxide = 0.0

            val dampening = DAMPENING

            // Gas diffusion:
            for (i in 0 until 6) {
                val neighbor = linkedCells[i]
                    ?: continue

                val neighborTank = neighbor.tank
                var delta: Double

                delta = tank.sulfurDioxide - neighborTank.sulfurDioxide
                if (delta >= epsilon) {
                    tempSulfurDioxide[i] = delta * dampening
                    sumSulfurDioxide += delta * dampening
                }

                delta = tank.steam - neighborTank.steam
                if (delta >= epsilon) {
                    tempSteam[i] = delta * dampening
                    sumSteam += delta * dampening
                }

                delta = tank.nitrogenDioxide - neighborTank.nitrogenDioxide
                if (delta >= epsilon) {
                    tempNitrogenDioxide[i] = delta * dampening
                    sumNitrogenDioxide += delta * dampening
                }
            }

            // Clamp each gas by max flow-rate and available source amount:
            val scaleSO2 = if (sumSulfurDioxide > 0.0) {
                min(1.0, min(MAX_GAS_FLOW_RATE / sumSulfurDioxide, tank.sulfurDioxide / sumSulfurDioxide))
            } else 1.0

            val scaleSteam = if (sumSteam > 0.0) {
                min(1.0, min(MAX_GAS_FLOW_RATE / sumSteam, tank.steam / sumSteam))
            } else 1.0

            val scaleNO2 = if (sumNitrogenDioxide > 0.0) {
                min(1.0, min(MAX_GAS_FLOW_RATE / sumNitrogenDioxide, tank.nitrogenDioxide / sumNitrogenDioxide))
            } else 1.0

            for (i in 0 until 6) {
                outboundBuffers.setSulfurDioxide(i, tempSulfurDioxide[i] * scaleSO2)
                outboundBuffers.setSteam(i, tempSteam[i] * scaleSteam)
                outboundBuffers.setNitrogenDioxide(i, tempNitrogenDioxide[i] * scaleNO2)
            }

            //  Acid falls as fast as the down-neighbor can accept. Any remainder equalizes horizontally.
            var sumSulfuricAcid = 0.0

            val downIdx = Direction.DOWN.get3DDataValue()
            val neighborDown = linkedCells[downIdx]

            if (neighborDown != null) {
                val downCapacity = neighborDown.tank.remainingCapacity
                val acidToDown = min(tank.sulfuricAcid, downCapacity)

                if (acidToDown >= epsilon) {
                    tempSulfuricAcid[downIdx] = acidToDown
                    sumSulfuricAcid += acidToDown
                }
            }

            // Spread remaining acid horizontally:
            val remainingAcid = tank.sulfuricAcid - sumSulfuricAcid
            if (remainingAcid >= epsilon) {
                for (dirIdx in HORIZONTAL_DIRECTIONS) {
                    val neighbor = linkedCells[dirIdx]
                        ?: continue

                    val delta = remainingAcid - neighbor.tank.sulfuricAcid

                    if (delta >= epsilon) {
                        tempSulfuricAcid[dirIdx] = delta * dampening
                        sumSulfuricAcid += delta * dampening
                    }
                }
            }

            // Clamp acid by max flow-rate and available source amount:
            val scaleAcid = if (sumSulfuricAcid > 0.0) {
                min(1.0, min(MAX_LIQUID_FLOW_RATE / sumSulfuricAcid, tank.sulfuricAcid / sumSulfuricAcid))
            }
            else 1.0

            for (i in 0 until 6) {
                outboundBuffers.setSulfuricAcid(i, tempSulfuricAcid[i] * scaleAcid)
            }
        }

        /**
         * Pass 2: each cell pulls what its neighbors want to send here.
         *
         * For each neighbor at direction `i`, we read `neighbor.outboundBuffers[opposite(i)]`, which is the amount the neighbor proposed to send toward us.
         * All incoming contributions from all neighbors are summed, then scaled by our own [FluidHandler.remainingCapacity] if the combined total exceeds what we can accept.
         *
         * A single combined capacity factor (not per-fluid) is correct because all 4 fluids share one pool in [FluidHandler].
         *
         * Source-side scaling was already applied in [proposalPass], so total drained from any source is bounded by its stock.
         */
        fun transferPass() {
            val tank = blockEntity.tank
            val linkedCells = blockEntity.linkedCells
            val oppositeDirs = OPPOSITE_DIRS

            // Sum all incoming from every neighbor's outbound buffers aimed at us:
            var totalIncoming = 0.0
            for (i in 0 until 6) {
                val neighbor = linkedCells[i]
                    ?: continue

                val neighborBuffer = neighbor.simulation.outboundBuffers
                val opposite = oppositeDirs[i]

                totalIncoming += neighborBuffer.getSulfuricAcid(opposite)
                totalIncoming += neighborBuffer.getSulfurDioxide(opposite)
                totalIncoming += neighborBuffer.getSteam(opposite)
                totalIncoming += neighborBuffer.getNitrogenDioxide(opposite)
            }

            if (totalIncoming < FractionalFluidStack.EPSILON) {
                return
            }

            // Capacity scale:
            val remainingCapacity = tank.remainingCapacity
            val capacityScale = if (totalIncoming > remainingCapacity) {
                remainingCapacity / totalIncoming
            } else 1.0

            // Execute transfers by pulling from each neighbor:
            for (i in 0 until 6) {
                val neighbor = linkedCells[i]
                    ?: continue

                val neighborTank = neighbor.tank
                val neighborSimulation = neighbor.simulation
                val neighborBuffer = neighborSimulation.outboundBuffers
                val opposite = oppositeDirs[i]

                val sulfuricAcid = neighborBuffer.getSulfuricAcid(opposite) * capacityScale
                val sulfurDioxide = neighborBuffer.getSulfurDioxide(opposite) * capacityScale
                val steam = neighborBuffer.getSteam(opposite) * capacityScale
                val nitrogenDioxide = neighborBuffer.getNitrogenDioxide(opposite) * capacityScale

                var dirty = false

                if (sulfuricAcid >= FractionalFluidStack.EPSILON) {
                    neighborTank.sulfuricAcid -= sulfuricAcid
                    tank.sulfuricAcid += sulfuricAcid
                    dirty = true
                }

                if (sulfurDioxide >= FractionalFluidStack.EPSILON) {
                    neighborTank.sulfurDioxide -= sulfurDioxide
                    tank.sulfurDioxide += sulfurDioxide
                    dirty = true
                }

                if (steam >= FractionalFluidStack.EPSILON) {
                    neighborTank.steam -= steam
                    tank.steam += steam
                    dirty = true
                }

                if (nitrogenDioxide >= FractionalFluidStack.EPSILON) {
                    neighborTank.nitrogenDioxide -= nitrogenDioxide
                    tank.nitrogenDioxide += nitrogenDioxide
                    dirty = true
                }

                if (dirty) {
                    hasTransferred = true
                    neighborSimulation.hasTransferred = true
                }
            }
        }

        fun setChangedIfRequired() {
            if (hasTransferred) {
                blockEntity.setChanged()
                hasTransferred = false
            }
        }
    }

    @ServerOnly
    val simulation = LeadChamberSimulation(this)

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        pTag.putDouble(SULFURIC_ACID, tank.sulfuricAcid)
        pTag.putDouble(SULFUR_DIOXIDE, tank.sulfurDioxide)
        pTag.putDouble(STEAM, tank.steam)
        pTag.putDouble(NITROGEN_DIOXIDE, tank.nitrogenDioxide)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        tank.sulfuricAcid = pTag.getDouble(SULFURIC_ACID)
        tank.sulfurDioxide = pTag.getDouble(SULFUR_DIOXIDE)
        tank.steam = pTag.getDouble(STEAM)
        tank.nitrogenDioxide = pTag.getDouble(NITROGEN_DIOXIDE)
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (!pLevel.isClientSide) {
            ensureRegistered()
            markForRebuild()
        }
    }

    @ServerOnly
    private var registeredWithManager = false

    @ServerOnly
    private fun ensureRegistered() {
        if (!registeredWithManager && level != null && !level!!.isClientSide) {
            LeadChamberExecutionManager.registerBlockEntity(this)
            registeredWithManager = true
        }
    }

    @ServerOnly
    private fun ensureUnregistered() {
        if (registeredWithManager) {
            LeadChamberExecutionManager.unregisterBlockEntity(this)
            registeredWithManager = false
        }
    }

    override fun onChunkUnloaded() {
        ensureUnregistered()
        super.onChunkUnloaded()
    }

    override fun setRemoved() {
        ensureUnregistered()
        super.setRemoved()
    }

    /**
     * Called during the tick, when a neighbor change is detected, after [markedForRebuildLinks] was set.
     */
    @ServerOnly
    fun rebuildLinksIfRequired() {
        requireIsOnServerThread()

        if (!markedForRebuildLinks) {
            return
        }

        markedForRebuildLinks = false

        val pos = blockPos!!
        val level = level as ServerLevel

        for (i in 0 until 6) {
            val face = Direction.from3DDataValue(i)
            val remotePos = pos + face

            if (!level.isLoaded(remotePos)) {
                linkedCells[i] = null
                continue
            }

            linkedCells[i] = level.getBlockEntity(remotePos) as? LeadChamberBlockEntity
        }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Acid: ${tank.sulfuricAcid.rounded()}" }
        builder.debugInIDE { "Sulfur Dioxide: ${tank.sulfurDioxide.rounded()}" }
        builder.debugInIDE { "Steam: ${tank.steam.rounded()}" }
        builder.debugInIDE { "Nitrogen Dioxide: ${tank.nitrogenDioxide.rounded()}" }
    }
}
