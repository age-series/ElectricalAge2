@file:Suppress("unused")

package org.eln2.mc.common.content.processing

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
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
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.*
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMass
import org.eln2.mc.*
import org.eln2.mc.client.render.foundation.FlwInstanceTypes
import org.eln2.mc.client.render.foundation.ThermalTint
import org.eln2.mc.client.render.foundation.TransformedLightOverrideInstance
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.ReplaceVanillaParticlesBlockExtension
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.fluids.foundation.BoilingTransformation
import org.eln2.mc.common.fluids.foundation.CondensationTransformation
import org.eln2.mc.common.fluids.foundation.FluidTransformationManager
import org.eln2.mc.common.fluids.foundation.PhysicalFluidManager
import org.eln2.mc.common.content.ThermalWireObject
import org.eln2.mc.common.content.WrenchInteractable
import org.eln2.mc.common.content.WrenchItem
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.fluids.foundation.*
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import org.ageseries.libage.mathematics.approxEq
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import org.eln2.mc.Locators
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.client.overlays.HoverDetailSupplier
import org.eln2.mc.mathematics.Base6Direction3dMask
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.min

/**
 * This system uses a novel architecture: exploiting the [CellGraph]'s construction to build a pseudo-multiblock and execute finely-grained server thread logic.
 * The structure is a sixtuply-linked list of [PhaseChangeModuleCell]s, which are always persistent and not subject to chunk unloads.
 * Each [PhaseChangeModuleCell] holds a list of (at most) 6 other module cells. When columns are placed/remove or neighbors change, or the graph loads from disk, the lists for the affected cells are rebuilt.
 * When we want to execute the actual logic, we simply exclude the cells whose block entities are not loaded.
 *
 * The distillation tower is a pseudo-multiblock in the sense that each module exchanges mass and energy with neighbor (see below what the neighbors are), but there isn't a pre-set structure. It's more like a cellular automata.
 * Neighbors are other distillation module block entities. Given a module, its neighbors may or may not be direct in-world neighbors. That distinction happens when distillation columns are used. Those don't simulate or hold anything; they are structural elements that connect modules vertically.
 * They act as perfectly insulated and volume-less pipes. They are improper cells, since they don't have simulation objects. The default connection logic doesn't allow them to connect to anything, but we override the logic to allow specifically connecting to distillation modules and other columns.
 *
 * The [PhaseChangeModuleCell] has a thermal body, which participates in the thermal simulation.
 * The cell does the distillation and multiblock transfer logic, by subscribing to [ServerPhase] events.
 * It allows us to execute logic in multiple passes before the simulations are dispatched on the pool (see the documentation of [ServerPhase.Start]).
 * This also means we don't need locking during the simulation.
 * We still set up a solver synchronization point, that is acquired when the thermal handler capability is accessed, to mutate the thermal body.
 * That happens during block entity ticks and whatnot (when machines try to access the tanks, for example).
 *
 * We do the simulation in 3 phases:
 * 1. Evaporation/Condensation and Transfer Recording
 *  - Phase change occurs. It is an internal process (doesn't involve neighbors), that basically transforms liquids into gases and gases into liquids within the module, and modifies the thermal energy and composition.
 *  - All outgoing fluid transfers to neighbors are recorded in a buffer
 * 2. Execute Transfers
 *  - Mass transfers are scaled based on the capacity in each receiving tank and then executed. Cells that changed are marked.
 * 3. Recompute Properties
 *  - If a module received/sent any fluid in the second phase, it recalculates the thermal body's composition and mass.
 * */

/**
 * Improper cell, used only to facilitate the linkage between [PhaseChangeModuleCell].
 * But when a series of columns links two modules, the modules won't act as if they are adjacent; there is a separate rule for that.
 * See the module itself for more information.
 * */
class DistillationColumnCell(ci: CellCreateInfo) : Cell(ci) {
    /**
     * Overrides the connection logic to allow the linkage.
     * */
    override fun allowsConnection(remote: Cell): Boolean {
        if(remote !is DistillationColumnCell && remote !is PhaseChangeModuleCell) {
            return false
        }

        val localBlockPos = locator.requireLocator(Locators.BLOCK)
        val remoteBlockPos = remote.locator.requireLocator(Locators.BLOCK)

        return remoteBlockPos == localBlockPos.above() || remoteBlockPos == localBlockPos.below()
    }
}

class DistillationColumnBlock : UprightHorizontalDirectionCellBlock<DistillationColumnCell>() {
    override fun getCellProvider() = Eln2Processing.DISTILLATION_COLUMN_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = DistillationColumnBlockEntity(pPos, pState)

    /**
     * Overrides the scan logic to allow the linkage.
     * */
    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        val pos = cell.locator.requireLocator(Locators.BLOCK)

        Base6Direction3dMask.VERTICALS.forEach { dir ->
            val targetBlockEntity = level.getBlockEntity(pos + dir)

            // For some reason, the smart cast doesn't build if I combine the conditions
            if(targetBlockEntity is DistillationColumnBlockEntity) {
                results.add(CellAndContainerHandle.captureInScope(targetBlockEntity.cell))
            }
            else if(targetBlockEntity is PhaseChangeModuleBlockEntity) {
                results.add(CellAndContainerHandle.captureInScope(targetBlockEntity.cell))
            }
        }
    }
}

/**
 * Listens for cells connecting to this column to incrementally construct the linked grid.
 * */
class DistillationColumnBlockEntity(pos: BlockPos, state: BlockState) : CellBlockEntity<DistillationColumnCell>(pos, state, Eln2Processing.DISTILLATION_COLUMN_BLOCK_ENTITY.get()), ComponentDisplay {
    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Graph: ${cell.graph.id}" }
    }

    /**
     * Since a column may not be a direct neighbor of the affected modules, we need to traverse the columns to reach those modules.
     * */
    private fun sendNotification() {
        val visited = HashSet<Cell>()
        val queue = ArrayDeque<Cell>()

        queue.addAll(cell.connections)

        while (queue.isNotEmpty()) {
            val front = queue.removeFirst()

            if(!visited.add(front)) {
                continue
            }

            if(front is PhaseChangeModuleCell) {
                front.markForRebuild()
            }
            else {
                queue.addAll(front.connections)
            }
        }
    }

    override fun onCellDisconnected(actualCell: Cell, remoteCell: Cell) {
        sendNotification()
        super.onCellDisconnected(actualCell, remoteCell)
    }

    override fun onCellConnected(actualCell: Cell, remoteCell: Cell) {
        sendNotification()
        super.onCellConnected(actualCell, remoteCell)
    }
}

/**
 * Handles the distillation and pseudo-multiblock interaction. The distillation is not special, but the interactions are:
 * - The module will try to equalize liquid level with the horizontally adjacent modules (this also transfers heat, at least during the equalization)
 * - The module pushes all its liquid to the module below (reflux), **but doesn't follow columns to do that**. It allows making different sections separated by columns, where the products of each section accumulate at the bottom layer of the section. This also transfers heat.
 * - The module pushes gas into the module above (this does follow the columns). This also transfers heat.
 *
 * The distillation and the interactions are executed in [ServerPhase.Start] and its second and third passes, before the simulation runs.
 *
 * Also, thermal connections are only done horizontally. This might allow for more interesting builds (the modules adjacent above and below won't receive heat via conduction).
 *
 * @param allowExternalConnections If false, refuses cell connections to anything that isn't a distillation module that also has [allowExternalConnections] set to false.
 * */
class PhaseChangeModuleCell(ci: CellCreateInfo, leakage: ConnectionParameters, val maxTemperature: Quantity<Temperature>, val allowExternalConnections: Boolean) :
    Cell(ci),
    SidedThermalFLBR<PhaseChangeModuleCell>,
    SimulationExecutionSubgraph.SynchronizationPointCell<PhaseChangeModuleCell>
{
    companion object {
        val HULL_MATERIAL = ChemicalElement.Copper.asMaterial.copy(thermalConductivity = Quantity(3159.0, WATT_PER_METER_KELVIN))
        val HULL_MASS = Quantity(50.0, KILOGRAM)
    }

    override val thermalSize: ThermalSize
        get() = ThermalSize.Any

    @SimObject
    val wire = DistillationModuleThermalObject(this, ThermalMass(HULL_MATERIAL, mass = HULL_MASS), leakage)

    @Behavior
    val temperatureExplosion = ThermalBreakdownBehavior.create(maxTemperature, this, wire.thermalBody::temperature)

    /**
     * Used when the block entity's capability is accessed.
     * Needed to change the mass and composition of the thermal body.
     * */
    //#region Solver Lock

    val solverLock = SimulationExecutionSubgraph
        .SynchronizationPrimitive
        .Reentrant()

    val handle = ThermalObjectBasedFractionalFluidHandlerThermalExpansion.ThermalBodyHandle(
        wire.thermalBody,
        HULL_MATERIAL,
        HULL_MASS,
        solverLock.sync
    )

    override fun createSynchronizationMapForSubSolvers(obj: SimulationObject<*>, subSolvers: List<Any>): Map<Any, SimulationExecutionSubgraph.SynchronizationPrimitive>? {
        if(obj == wire) {
            check(subSolvers.size == 1)

            return mapOf(subSolvers.first() to solverLock)
        }

        return DEBUGGER_BREAK(null)
    }

    //#endregion

    @Replicator
    fun replicator(target: InternalTemperatureConsumer) =  InternalTemperatureReplicatorBehavior(target) {
        !wire.thermalBody.temperature
    }

    @Replicator
    fun activityReplicator(target: DistillationActivityConsumer) = DistillationActivityReplicatorBehavior(target) {
        distillation.activity
    }

    override fun saveCellData() : CompoundTag {
        val tag = CompoundTag()
        tag.put("material", wire.thermalBody.material.saveNbt())
        tag.putQuantity("mass", wire.thermalBody.mass)
        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        wire.thermalBody.material = tag.getCompound("material").getMaterial()
        wire.thermalBody.mass = tag.getQuantity("mass")
    }

    /**
     * Permits connections with the columns, on top of the default logic.
     * */
    override fun allowsConnection(remote: Cell): Boolean {
        if(!allowExternalConnections) {
            return remote is PhaseChangeModuleCell && !remote.allowExternalConnections
        }

        if(remote is DistillationColumnCell) {
            val localBlockPos = locator.requireLocator(Locators.BLOCK)
            val remoteBlockPos = remote.locator.requireLocator(Locators.BLOCK)

            return remoteBlockPos == localBlockPos.above() || remoteBlockPos == localBlockPos.below()
        }

        return super.allowsConnection(remote)
    }

    //#region Server Thread Code

    /**
     * Fetches the neighbor module cells.
     * */
    @OnServerThread
    override fun onWorldLoadedPreSim() {
        markForRebuild()
        super.onWorldLoadedPreSim()
    }

    @OnServerThread
    private var markedForRebuildLinks = false

    /**
     * Marks that the structure of the multiblock has changed, and the linked modules will be rebuilt on the next [serverTickStart].
     * */
    @OnServerThread
    fun markForRebuild() {
        markedForRebuildLinks = true
    }

    /**
     * The neighbor cells, indexed by [Direction.get3DDataValue].
     * */
    @OnServerThread
    var linkedCells = Array<PhaseChangeModuleCell?>(6) { null }

    class DistillationModuleThermalObject(cell: Cell, thermalBody: ThermalMass, environmentLeakageParameters: ConnectionParameters) : ThermalWireObject(cell, thermalBody, environmentLeakageParameters) {
        /**
         * Increases conduction between distillation modules (to emulate the liquid transferring heat).
         * */
        override fun getParameters(remote: ThermalObject<*>): ConnectionParameters {
            if(remote !is DistillationModuleThermalObject) {
                return super.getParameters(remote)
            }

            return ConnectionParameters.DEFAULT.copy(
                conductance = Quantity(100.0)
            )
        }
    }

    /**
     * The complete distillation simulation. It handles phase change and thermal fluid transfer to neighbors.
     * The thermal fluid transfer is implemented in multiple passes, like a cellular automata.
     * All logic runs on the game thread (because we are accessing the fluid storage a lot), before the simulation runs, so it doesn't need locks during the simulation.
     * */
    @OnServerThread
    class PhaseChangeSimulation(val cell: PhaseChangeModuleCell) {
        companion object {
            // P.S. don't adjust this array, the indices are used in logic.
            val LIQUID_TARGETS = intArrayOf(
                Direction.NORTH.get3DDataValue(),
                Direction.WEST.get3DDataValue(),
                Direction.SOUTH.get3DDataValue(),
                Direction.EAST.get3DDataValue(),
                Direction.DOWN.get3DDataValue()
            )

            const val MAX_PHASE_CHANGE_RATE = 1.0
            const val DAMPENING_CONSTANT = 1.0 / 6.0
            const val MAX_LIQUID_FLOW_RATE = 10.0
            const val MAX_GAS_FLOW_RATE = 1500.0
        }

        //#region Setup State

        /**
         * Temperature stored at the start of the simulation, after the phase change has occurred. All fluids exiting will be exiting at this temperature.
         * */
        var transferTemperature = Quantity<Temperature>(Double.NaN)
            private set

        /**
         * The block entity associated with the [cell].
         * */
        var blockEntity: PhaseChangeModuleBlockEntity? = null
            private set

        /**
         * The block entities associated with each cell in [linkedCells]. All of them are in scope.
         * */
        val targetBlockEntities = Array<PhaseChangeModuleBlockEntity?>(6) { null }

        /**
         * If true, the [blockEntity] is not in scope.
         * */
        private var skipSimulation = false

        //#endregion

        @Suppress("NOTHING_TO_INLINE")
        class TransferBuffer {
            var count = 0
            var fluidArray = emptyArray<Fluid>()
            var amountArray = DoubleArray(0)
            var targetTankArray = ByteArray(0)

            enum class TargetTank(val id: Byte) {
                Liquid(1),
                Gas(2);

                companion object {
                    inline fun of(id: Byte) = when(id.toInt()) {
                        1 -> Liquid
                        2 -> Gas
                        else -> error("Invalid target tank $id")
                    }
                }
            }

            inline fun push(fluid: Fluid, amount: Double, targetTank: TargetTank) {
                if(count == fluidArray.size) {
                    fluidArray = fluidArray.toList().plus(Fluids.EMPTY).toTypedArray()
                    amountArray = amountArray.toList().plus(Double.NaN).toDoubleArray()
                    targetTankArray = targetTankArray.toList().plus(-1).toByteArray()
                }

                fluidArray[count] = fluid
                amountArray[count] = amount
                targetTankArray[count] = targetTank.id
                ++count
            }

            fun clear() {
                count = 0

                for (i in 0 until fluidArray.size) {
                    fluidArray[i] = Fluids.EMPTY
                    amountArray[i] = Double.NaN
                    targetTankArray[i] = -1
                }
            }
        }

        val outboundBuffers = Array<TransferBuffer>(6) { TransferBuffer() }

        /**
         * Set when transfers occur. Marks that a recalculation of the composition is needed.
         * */
        var hasTransferred = false

        /**
         * Total amount boiled this step, in [PhaseChangeSimulation.MAX_PHASE_CHANGE_RATE] units.
         * Reset by [phaseChange].
         * */
        var boiledAmount = 0.0
            private set

        /**
         * Total amount condensed this step, in [PhaseChangeSimulation.MAX_PHASE_CHANGE_RATE] units.
         * Reset by [phaseChange].
         * */
        var condensedAmount = 0.0
            private set

        /**
         * Normalized phase-change activity in [0, 1], combining evaporation and condensation.
         * */
        val activity: Double
            get() = ((boiledAmount + condensedAmount) / (2.0 * MAX_PHASE_CHANGE_RATE)).coerceIn(0.0, 1.0)

        /**
         * Fetches the block entity associated with [cell] into [blockEntity] and, if that's all good and in scope, fetches the target block entities that are in scope and loads them into [targetBlockEntities].
         * If our block entity is not in scope, we will [skipSimulation].
         * */
        fun prepareForSimulation() {
            skipSimulation = true

            if(cell.container == null) {
                /**
                 * The block entity is unloaded.
                 * */
                return
            }

            skipSimulation = false

            blockEntity = cell.container as PhaseChangeModuleBlockEntity

            for (i in 0 until 6) {
                val targetCell = cell.linkedCells[i]
                    ?: continue

                val blockPos = targetCell.locator.requireLocator(Locators.BLOCK)

                if(!cell.graph.level.isLoaded(blockPos)) {
                    /**
                     * Skip (it's a horizontal neighbor, most likely).
                     * */
                    continue
                }

                val targetBlockEntity = targetCell.container as? PhaseChangeModuleBlockEntity

                if(targetBlockEntity == null) {
                    DEBUGGER_BREAK()
                    continue
                }

                targetBlockEntities[i] = targetBlockEntity
            }
        }

        private val phaseChangeVisited = HashSet<Fluid>()

        /**
         * Evaporates liquids from the liquid tank. The amount to evaporate is, at most, [MAX_PHASE_CHANGE_RATE].
         * Fluids with a lower boiling point have priority.
         * */
        private fun evaporation() {
            val blockEntity = blockEntity!!
            val liquidTank = blockEntity.liquidTank
            val gasTank = blockEntity.gasTank

            val body = cell.wire.thermalBody
            var remainingEvaporation = MAX_PHASE_CHANGE_RATE
            while (remainingEvaporation > 0.0 && liquidTank.fluids.isNotEmpty() && gasTank.remainingCapacity > 0.0) {
                /**
                 * Selects stack with the lowest boiling point:
                 * */
                var target: FractionalFluidStack? = null
                var boiling: BoilingTransformation? = null
                for (stack in liquidTank.fluids) {
                    if(phaseChangeVisited.contains(stack.fluid)) {
                        continue
                    }

                    val transformation = FluidTransformationManager.getTransformations(stack.fluid)?.boiling
                        ?: continue

                    if(target == null || transformation.temperature < boiling!!.temperature) {
                        target = stack
                        boiling = transformation
                    }
                }

                if(target == null) {
                    break
                }

                boiling!!

                phaseChangeVisited.add(target.fluid)
                val temperature = body.temperature
                val dT = !temperature - !boiling.temperature

                if(dT <= 0.0) {
                    /**
                     * No boiling can occur:
                     * */
                    break
                }

                /**
                 * Calculates the total fluid to boil by applying constraints:
                 * */
                var amountToBoil = remainingEvaporation

                /**
                 * Constrain by available liquid:
                 * */
                amountToBoil = min(amountToBoil, target.amount)

                /**
                 * Constrain by the energy limit:
                 * */
                amountToBoil = min(amountToBoil, dT * !body.mass * !body.material.specificHeat / !boiling.enthalpy)

                val gasProperties = PhysicalFluidManager.requireProperties(boiling.resultGas)
                val gasExpansionFactor = gasProperties.gasExpansionFactor

                /**
                 * Constrain by the remaining capacity in the gas tank:
                 * */
                amountToBoil = min(amountToBoil, gasTank.remainingCapacity * 1000.0 / boiling.resultGasProportion / gasExpansionFactor)

                if (amountToBoil < FractionalFluidStack.EPSILON) {
                    continue
                }

                val gasGenerated = amountToBoil * boiling.resultGasProportion / 1000.0 * gasExpansionFactor

                if(gasGenerated < FractionalFluidStack.EPSILON) {
                    /**
                     * If the amount is too low, then it will round down to 0, so we can't do anything.
                     * */
                    continue
                }

                var residueStack: FractionalFluidStack? = null
                if(boiling.resultLiquidResidue != null) {
                    val residue = amountToBoil * (1000.0 - boiling.resultGasProportion) / 1000.0

                    if(residue >= FractionalFluidStack.EPSILON) {
                        /**
                         * Assume quantity lost. It is better not to stall the main transformation for this crap.
                         * */
                        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                        residueStack = FractionalFluidStack(boiling.resultLiquidResidue!!, residue)
                    }
                }

                val liquidProperties = PhysicalFluidManager.requireProperties(target.fluid)

                val liquidCapacity = amountToBoil * !liquidProperties.specificHeatCapacity
                val gasCapacity = gasGenerated * !gasProperties.specificHeatCapacity / gasExpansionFactor

                val residueCapacity = if (residueStack != null) {
                    val residueProperties = PhysicalFluidManager.requireProperties(residueStack.fluid)
                    residueStack.amount * !residueProperties.specificHeatCapacity
                }
                else {
                    0.0
                }

                liquidTank.drainFractional(FractionalFluidStack(target.fluid, amountToBoil), IFluidHandler.FluidAction.EXECUTE)
                gasTank.fillFractional(FractionalFluidStack(boiling.resultGas, gasGenerated), IFluidHandler.FluidAction.EXECUTE)

                if(residueStack != null) {
                    liquidTank.fillFractional(residueStack, IFluidHandler.FluidAction.EXECUTE)
                }

                val sensibleCorrection = ((gasCapacity + residueCapacity) - liquidCapacity) * !temperature
                val latentHeat = !boiling.enthalpy * amountToBoil

                body.energy += Quantity(sensibleCorrection - latentHeat, JOULE)
                remainingEvaporation -= amountToBoil
                boiledAmount += amountToBoil

                cell.setChanged()
                blockEntity.setChanged()
                blockEntity.recalculateBody()
            }

            phaseChangeVisited.clear()
        }

        /**
         * Condenses gases from the gas tank. The amount to condense is, at most, [MAX_PHASE_CHANGE_RATE].
         * Fluids with a higher condensation point have priority.
         * */
        private fun condensation() {
            val blockEntity = blockEntity!!
            val liquidTank = blockEntity.liquidTank
            val gasTank = blockEntity.gasTank

            val body = cell.wire.thermalBody
            var remainingCondensation = MAX_PHASE_CHANGE_RATE
            while (remainingCondensation > 0.0 && gasTank.fluids.isNotEmpty() && liquidTank.remainingCapacity > 0.0) {
                /**
                 * Selects stack with the highest condensation point:
                 * */
                var target: FractionalFluidStack? = null
                var condensation: CondensationTransformation? = null
                for (stack in gasTank.fluids) {
                    if(phaseChangeVisited.contains(stack.fluid)) {
                        continue
                    }

                    val transformation = FluidTransformationManager.getTransformations(stack.fluid)?.condensation
                        ?: continue

                    if(target == null || transformation.temperature > condensation!!.temperature) {
                        target = stack
                        condensation = transformation
                    }
                }

                if(target == null) {
                    break
                }

                phaseChangeVisited.add(target.fluid)

                condensation!!

                val temperature = body.temperature
                val dT = !condensation.temperature - !temperature

                if(dT <= 0.0) {
                    break
                }

                /**
                 * Calculates the total gas to condense by applying constraints:
                 * */
                val gasProperties = PhysicalFluidManager.requireProperties(target.fluid)
                val gasExpansionFactor = gasProperties.gasExpansionFactor
                var amountToCondense = remainingCondensation * gasExpansionFactor

                /**
                 * Constrain by available gas:
                 * */
                amountToCondense = min(amountToCondense, target.amount)

                /**
                 * Constrain by the energy limit:
                 * */
                amountToCondense = min(amountToCondense, dT * !body.mass * !body.material.specificHeat * gasExpansionFactor / !condensation.enthalpy)

                /**
                 * Constrain by remaining capacity in the liquid tank:
                 * */
                amountToCondense = min(amountToCondense, liquidTank.remainingCapacity * 1000.0 / condensation.resultLiquidProportion * gasExpansionFactor)

                if(amountToCondense < FractionalFluidStack.EPSILON) {
                    continue
                }

                val liquidGenerated = amountToCondense * condensation.resultLiquidProportion / 1000.0 / gasExpansionFactor

                if(liquidGenerated < FractionalFluidStack.EPSILON) {
                    /**
                     * If the amount is too low, then it will round down to 0, so we can't do anything.
                     * */
                    continue
                }

                var residueStack: FractionalFluidStack? = null

                if(condensation.resultGasResidue != null) {
                    val residue = amountToCondense * (1000.0 - condensation.resultLiquidProportion) / 1000.0

                    if(residue >= FractionalFluidStack.EPSILON) {
                        /**
                         * Assume quantity lost. It is better not to stall the main transformation for this crap.
                         * */
                        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                        residueStack = FractionalFluidStack(condensation.resultGasResidue!!, residue)
                    }
                }

                val liquidProperties = PhysicalFluidManager.requireProperties(condensation.resultLiquid)

                val gasCapacity = amountToCondense * !gasProperties.specificHeatCapacity / gasExpansionFactor
                val liquidCapacity = liquidGenerated * !liquidProperties.specificHeatCapacity

                val residueCapacity = if (residueStack != null) {
                    val residueProperties = PhysicalFluidManager.requireProperties(residueStack.fluid)
                    residueStack.amount * !residueProperties.specificHeatCapacity / residueProperties.gasExpansionFactor
                }
                else {
                    0.0
                }

                gasTank.drainFractional(FractionalFluidStack(target.fluid, amountToCondense), IFluidHandler.FluidAction.EXECUTE)
                liquidTank.fillFractional(FractionalFluidStack(condensation.resultLiquid, liquidGenerated), IFluidHandler.FluidAction.EXECUTE)

                if(residueStack != null) {
                    gasTank.fillFractional(residueStack, IFluidHandler.FluidAction.EXECUTE)
                }

                val sensibleCorrection = ((liquidCapacity + residueCapacity) - gasCapacity) * !temperature
                val latentHeat = !condensation.enthalpy * (amountToCondense / gasExpansionFactor)
                body.energy += Quantity(sensibleCorrection + latentHeat, JOULE)
                remainingCondensation -= amountToCondense / gasExpansionFactor
                condensedAmount += amountToCondense / gasExpansionFactor

                cell.setChanged()
                blockEntity.setChanged()
                blockEntity.recalculateBody()
            }

            phaseChangeVisited.clear()
        }

        /**
         * Executes evaporation and condensation.
         * */
        fun phaseChange() {
            boiledAmount = 0.0
            condensedAmount = 0.0

            if(skipSimulation) {
                return
            }

            evaporation()
            condensation()
        }

        /**
         * Records liquids sent to neighbors.
         * We equalize with horizontal neighbors (acting as a large multiblock tank) and push to the neighbor under (condenser reflux).
         * */
        private fun recordOutgoingLiquids() {
            val liquidTank = blockEntity!!.liquidTank

            if(liquidTank.fluids.isEmpty()) {
                return
            }

            /**
             * Checks if we should be refluxing.
             * We only do so if the connection to the neighbor below is direct.
             * */
            val refluxes = targetBlockEntities[Direction.DOWN.get3DDataValue()].let {
                if(it == null) {
                    false
                }
                else {
                    it.locator.requireLocator(Locators.BLOCK) == cell.locator.requireLocator(Locators.BLOCK).below()
                }
            }

            /**
             * Algorithm: For each fluid we have, we calculate the 5 transfers toward each neighbor. For the horizontals, we try to equalize with them, and for downward, we try to push all.
             * We need a temporary buffer, where we write the liquid to transfer to each neighbor not taking into account the previous transfers we calculated.
             * When we have all of those, we can normalize them so they add up to, at most, the amount of fluid we have, and we also apply the transfer rate limit.
             * */
            val transferList = DoubleArray(5)
            for (fluidIdx in 0 until liquidTank.fluids.size) {
                val sourceStack = liquidTank.fluids[fluidIdx]
                val fluid = sourceStack.fluid

                /**
                 * Calculates the upper bound on the fluid leaving our stack toward neighbors:
                 * */
                var candidateTotalOutflow = 0.0
                for (j in 0 until 5) {
                    val neighbor = targetBlockEntities[LIQUID_TARGETS[j]]

                    if(neighbor == null) {
                        transferList[j] = 0.0
                        continue
                    }

                    val transferToNeighbor = if(j == 4) {
                        if(refluxes) {
                            /**
                             * For the reflux tank, transfer regardless:
                             * */
                            sourceStack.amount
                        }
                        else {
                            0.0
                        }
                    }
                    else {
                        /**
                         * For everything else, try to equalize:
                         * */
                        DAMPENING_CONSTANT * 0.5 * (sourceStack.amount - neighbor.liquidTank.getAmountOf(fluid))
                    }

                    if(transferToNeighbor > FractionalFluidStack.EPSILON) {
                        candidateTotalOutflow += transferToNeighbor
                        transferList[j] = transferToNeighbor
                    }
                    else {
                        transferList[j] = 0.0
                    }
                }

                if(candidateTotalOutflow < FractionalFluidStack.EPSILON) {
                    /**
                     * Nothing to transfer:
                     * */
                    continue
                }

                /**
                 * Calculates a scale factor so the outflow doesn't exceed the amount in the stack and the max flow rate:
                 * */
                val scaleFactor = min(min(candidateTotalOutflow, sourceStack.amount), MAX_LIQUID_FLOW_RATE) / candidateTotalOutflow

                /**
                 * Push valid transfers to buffer:
                 * */
                for (j in 0 until 5) {
                    val amount = transferList[j] * scaleFactor

                    if(amount >= FractionalFluidStack.EPSILON) {
                        val buffer = outboundBuffers[LIQUID_TARGETS[j]]

                        buffer.push(fluid, amount, TransferBuffer.TargetTank.Liquid)
                    }

                }
            }
        }

        /**
         * Records gases rising to the neighbor above.
         * */
        private fun recordOutgoingGases() {
            val neighborDir = Direction.UP.get3DDataValue()

            if(targetBlockEntities[neighborDir] == null) {
                return
            }

            val gasTank = blockEntity!!.gasTank

            if (gasTank.fluids.isEmpty()) {
                return
            }

            /**
             * Instead of equalized transfer, we actually push by density (as if the lighter gas rises first):
             * */
            gasTank.fluids.sortBy {
                PhysicalFluidManager.requireProperties(it.fluid).density
            }

            val buffer = outboundBuffers[neighborDir]
            var remainingTransfer = MAX_GAS_FLOW_RATE
            for (fluidIdx in 0 until gasTank.fluids.size) {
                val sourceStack = gasTank.fluids[fluidIdx]

                val transfer = min(sourceStack.amount, remainingTransfer)
                buffer.push(sourceStack.fluid, transfer, TransferBuffer.TargetTank.Gas)
                remainingTransfer -= transfer

                if(remainingTransfer < FractionalFluidStack.EPSILON) {
                    break
                }
            }
        }

        /**
         * Calculates the transfers to each neighbor (upper bound, we can't calculate them exactly since multiple modules might push into that neighbor, and we can scrape against the fluid capacity) and stores them in [outboundBuffers].
         * */
        fun initialPass() {
            transferTemperature = cell.wire.thermalBody.temperature

            if(skipSimulation) {
                return
            }

            recordOutgoingLiquids()
            recordOutgoingGases()
        }

        /**
         * Second pass.
         * Now that the proposed transfers are all known, we can normalize them based on the remaining capacity and execute mass and energy transfer.
         * */
        fun transferPass() {
            if(skipSimulation) {
                return
            }

            val blockEntity = blockEntity!!
            val targetBlockEntities = targetBlockEntities

            /**
             * First, we calculate the total incoming fluid into this cell
             * */
            var incomingLiquid = 0.0
            var incomingGas = 0.0
            for (i in 0 until 6) {
                val neighbor = targetBlockEntities[i]
                    ?: continue

                /**
                 * Gets the fluids coming from the neighbor into this cell:
                 * */
                val incomingBuffer = neighbor.cell.distillation.outboundBuffers[
                    Direction.from3DDataValue(i).opposite.get3DDataValue()
                ]

                for (message in 0 until incomingBuffer.count) {
                    val amount = incomingBuffer.amountArray[message]
                    val type = TransferBuffer.TargetTank.of(incomingBuffer.targetTankArray[message])

                    when(type) {
                        TransferBuffer.TargetTank.Liquid -> incomingLiquid += amount
                        TransferBuffer.TargetTank.Gas -> incomingGas += amount
                    }
                }
            }

            val liquidTank = blockEntity.liquidTank
            val gasTank = blockEntity.gasTank

            val remainingLiquidCapacity = liquidTank.remainingCapacity
            val remainingGasCapacity = gasTank.remainingCapacity

            val liquidFactor = if(incomingLiquid > remainingLiquidCapacity) {
                remainingLiquidCapacity / incomingLiquid
            }
            else {
                1.0
            }

            val gasFactor = if(incomingGas > remainingGasCapacity) {
                remainingGasCapacity / incomingGas
            }
            else {
                1.0
            }

            /**
             * Execute transfers:
             * */
            val thermalBody = cell.wire.thermalBody
            for (i in 0 until 6) {
                val neighbor = targetBlockEntities[i]
                    ?: continue

                /**
                 * Gets the fluids coming from the neighbor into this cell:
                 * */
                val incomingBuffer = neighbor.cell.distillation.outboundBuffers[
                    Direction.from3DDataValue(i).opposite.get3DDataValue()
                ]

                for (message in 0 until incomingBuffer.count) {
                    var amount = incomingBuffer.amountArray[message]
                    val type = TransferBuffer.TargetTank.of(incomingBuffer.targetTankArray[message])

                    amount *= when(type) {
                        TransferBuffer.TargetTank.Liquid -> {
                            liquidFactor
                        }

                        TransferBuffer.TargetTank.Gas -> {
                            gasFactor
                        }
                    }

                    if(amount < FractionalFluidStack.EPSILON) {
                        /**
                         * Skip transfer:
                         * */
                        continue
                    }

                    /**
                     * Execute mass transfer. Also, it's not strictly necessary to use the amount given by drain, but jrddunbr recommends it:
                     * */
                    var resource = FractionalFluidStack(incomingBuffer.fluidArray[message], amount)
                    when(type) {
                        TransferBuffer.TargetTank.Liquid -> {
                            resource = neighbor.liquidTank.drainFractional(resource, IFluidHandler.FluidAction.EXECUTE)
                            amount = resource.amount
                            liquidTank.fillFractional(resource, IFluidHandler.FluidAction.EXECUTE)
                        }
                        TransferBuffer.TargetTank.Gas -> {
                            resource = neighbor.gasTank.drainFractional(resource, IFluidHandler.FluidAction.EXECUTE)
                            amount = resource.amount
                            gasTank.fillFractional(resource, IFluidHandler.FluidAction.EXECUTE)
                        }
                    }

                    /**
                     * Execute energy transfer:
                     * */
                    val fluidProperties = PhysicalFluidManager.requireProperties(resource.fluid)
                    val energy = Quantity(amount * !fluidProperties.specificHeatCapacity * !neighbor.cell.distillation.transferTemperature / fluidProperties.gasExpansionFactor, JOULE)
                    thermalBody.energy += energy
                    neighbor.cell.wire.thermalBody.energy -= energy

                    blockEntity.setChanged()
                    cell.setChanged()
                    hasTransferred = true

                    neighbor.setChanged()
                    neighbor.cell.setChanged()
                    neighbor.cell.distillation.hasTransferred = true
                }
            }
        }

        /**
         * Third and final pass.
         * If we transferred anything, then we need to recalculate the thermal body's material and mass.
         * Also clears all references and prepares for the next step.
         * */
        fun finalizePass() {
            if(hasTransferred) {
                blockEntity!!.recalculateBody()
            }

            skipSimulation = false
            blockEntity = null

            for (dir in 0 until 6) {
                targetBlockEntities[dir] = null
                outboundBuffers[dir].clear()
            }

            hasTransferred = false
        }
    }

    val distillation = PhaseChangeSimulation(this)

    /**
     * Registers 3 passes on the server thread.
     * */
    @OnServerThread
    override fun subscribeServerThread(subscribers: SubscriberCollection<ServerPhase>) {
        subscribers.addStart(this::serverTickStart)
        subscribers.addAfterStart1(this::serverTickAfterStart1)
        subscribers.addAfterStart2(this::serverTickAfterStart2)
    }

    /**
     * Finds the (up to) 6 target distillation module cells.
     * The horizontal ones are found trivially, but the vertical ones may require following the [DistillationColumnCell]s to reach.
     * */
    @OnServerThread
    private fun rebuildLinksIfRequired() {
        if (!markedForRebuildLinks) {
            return
        }

        markedForRebuildLinks = false

        for (i in 0 until 6) {
            linkedCells[i] = null
        }

        val pos = locator.requireLocator(Locators.BLOCK)

        connections.forEach { other ->
            if(other is PhaseChangeModuleCell) {
                val remotePos = other.locator.requireLocator(Locators.BLOCK)

                val face = pos.directionTo(remotePos)
                    ?: error(DEBUGGER_BREAK("Invalid remote distillation $pos $remotePos"))

                linkedCells[face.get3DDataValue()] = other
            }
            else if(other is DistillationColumnCell) {
                val remotePos = other.locator.requireLocator(Locators.BLOCK)

                /**
                 * Up or Down. We will follow this direction until we reach a module.
                 * */
                val face = pos.directionTo(remotePos)
                    ?: error(DEBUGGER_BREAK("Invalid remote distillation column $pos $remotePos"))

                var current: Cell? = other
                while (current != null) {
                    if(current is PhaseChangeModuleCell) {
                        linkedCells[face.get3DDataValue()] = current
                        break
                    }

                    val currentPos = current.locator.requireLocator(Locators.BLOCK)

                    current = current.connections.firstOrNull {
                        currentPos.directionTo(it.locator.requireLocator(Locators.BLOCK)) == face
                    }
                }
            }
        }
    }

    @OnServerThread
    private fun serverTickStart(dt: Double, phase: ServerPhase) {
        rebuildLinksIfRequired()
        distillation.prepareForSimulation()
        distillation.phaseChange()
        distillation.initialPass()
    }

    @OnServerThread
    private fun serverTickAfterStart1(dt: Double, phase: ServerPhase) {
        distillation.transferPass()
    }

    @OnServerThread
    private fun serverTickAfterStart2(dt: Double, phase: ServerPhase) {
        distillation.finalizePass()
    }

    //#endregion
}

/**
 * @param isIncandescent If true, internal temperature will be synchronized and thermal tint will be applied.
 * */
data class PhaseChangeModuleModel(val isIncandescent: Boolean, val modelSupplier: Supplier<PartialModel>)

class PhaseChangeModuleBlock(
    val cell: RegistryObject<CellProvider<PhaseChangeModuleCell>>,
    val capacity: Int,
    val blockEntityType: RegistryObject<BlockEntityType<PhaseChangeModuleBlockEntity>>,
    val model: PhaseChangeModuleModel
) : UprightHorizontalDirectionCellBlock<PhaseChangeModuleCell>() {
    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentState: BlockState, pDirection: Direction) = true

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    override fun getCellProvider() = cell.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = PhaseChangeModuleBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (pLevel.isClientSide) {
            return BlockEntityTicker { _, _, _, pBlockEntity ->
                if (pBlockEntity is PhaseChangeModuleBlockEntity) {
                    pBlockEntity.clientTick()
                }
            }
        }

        return null
    }

    /**
     * Adds the vertical neighbor modules (we excluded verticals from the thermal connections, see the cell as for why), and adds the columns.
     * */
    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        super.spatialNeighborScan(level, results, cell)

        val pos = cell.locator.requireLocator(Locators.BLOCK)
        Base6Direction3dMask.VERTICALS.forEach { dir ->
            val targetBlockEntity = level.getBlockEntity(pos + dir)

            // For some reason, the smart cast doesn't build if I combine the conditions
            if(targetBlockEntity is DistillationColumnBlockEntity) {
                results.add(CellAndContainerHandle.captureInScope(targetBlockEntity.cell))
            }
            else if(targetBlockEntity is PhaseChangeModuleBlockEntity) {
                // We only allow thermal connections horizontally (I thought it's more interesting that way, and we can just couple layers with external conduits if we need to),
                // so we normally wouldn't connect to modules above and below.
                // We allow the connection explicitly here:
                results.add(CellAndContainerHandle.captureInScope(targetBlockEntity.cell))
            }
        }
    }
}

/**
 * Handles incremental building just like [DistillationColumnBlockEntity] and capability and sync.
 * Doesn't actually tick, all the server-side logic is done by the [PhaseChangeModuleCell].
 * */
class PhaseChangeModuleBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity<PhaseChangeModuleCell>(pos, state, (state.block as PhaseChangeModuleBlock).blockEntityType.get()),
    ComponentDisplay,
    WrenchInteractable,
    InternalTemperatureConsumer,
    DistillationActivityConsumer,
    BulkPacketHandlerBlockEntity,
    HoverDetailSupplier
{
    //#region Capability

    val liquidTank = MultipleFractionalFluidTank(0.0, true)
    val gasTank = MultipleFractionalFluidTank(0.0, true)

    @Suppress("unused")
    val capacityConstraint = FractionalFluidTankCapacityConstraint(
        (state.block as PhaseChangeModuleBlock).capacity.toDouble(),
        arrayOf(liquidTank, gasTank),
        doubleArrayOf((state.block as PhaseChangeModuleBlock).capacity.toDouble() * 0.9, (state.block as PhaseChangeModuleBlock).capacity.toDouble() * 0.9)
    )

    init {
        liquidTank.onVersionChanged += this::setChanged
        gasTank.onVersionChanged += this::setChanged
    }

    /**
     * Gets an iterable over all fluids in the hull, for thermal calculations.
     * */
    private fun getFluids() = ListCombination(liquidTank.fluids, gasTank.fluids)

    /**
     * Final wrapper around [parent] that accounts for the thermal exchanges.
     * The fill and drain behavior is implemented by [parent], and the thermal fill and drain are implemented by this wrapper, by delegating the actual fluid ops to [parent] and then doing the thermal changes here.
     * @param parent The wrapper that implements the filling/draining behavior.
     *
     * If you're reading this as a reference (heh, another contributor?), then the idea here is this:
     * - We implement normal [IFractionalFluidHandler]s for the faces (that have whatever filtering logic we need).
     * They could be [PurityBasedMultipleFractionalFluidTank] or [GravityBasedMultipleFractionalFluidTank].
     * In our case, [BottomFaceHandler], [SideHandler], [TopFaceHandler].
     * - Pass an instance of that as the [parent].
     *
     * Now, the [ThermalObjectBasedFractionalFluidHandlerThermalExpansion] will compose the thermal transfer logic on top of the transfer logic from [parent].
     * The thermal transfer logic will just mutate our thermal object.
     * */
    class ThermalLayer<Handler : IFractionalFluidHandler>(val blockEntity: PhaseChangeModuleBlockEntity, override val parent: Handler) : ThermalObjectBasedFractionalFluidHandlerThermalExpansion {
        override val handle: ThermalObjectBasedFractionalFluidHandlerThermalExpansion.ThermalBodyHandle
            get() = blockEntity.cell.handle

        override val ambientTemperature: Quantity<Temperature>
            get() = blockEntity.cell.environmentData.ambientTemperature

        override fun onMutated() = blockEntity.cell.setChanged()

        override fun getFluidStacks() = blockEntity.getFluids()
    }

    /**
     * Fluid handler for the bottom face:
     * - Allows extraction of residuals via [GravityBasedMultipleFluidTank]
     * - Allows insertion of gas and liquid
     * */
    class BottomFaceHandler(val liquidTank: MultipleFractionalFluidTank, val gasTank: MultipleFractionalFluidTank) : GravityBasedMultipleFractionalFluidTank(liquidTank) {
        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            val thermalFluid = PhysicalFluidManager.getProperties(resource.fluid)
                ?: return 0

            return if(thermalFluid.isGaseous) {
                gasTank.fill(resource, action)
            }
            else {
                liquidTank.fill(resource, action)
            }
        }

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            val thermalFluid = PhysicalFluidManager.getProperties(resource.fluid)
                ?: return 0.0

            return if(thermalFluid.isGaseous) {
                gasTank.fillFractional(resource, action)
            }
            else {
                liquidTank.fillFractional(resource, action)
            }
        }
    }

    val bottomFaceHandler = ThermalLayer(this, BottomFaceHandler(liquidTank, gasTank))
    val bottomFaceHandlerLazy: LazyOptional<ThermalLayer<BottomFaceHandler>> = LazyOptional.of { bottomFaceHandler }

    /**
     * Fluid handler for the top face:
     * - Allows extraction of gas via [PurityBasedMultipleFluidTank]
     * - Allows insertion of liquid
     * */
    class TopFaceHandler(val liquidTank: MultipleFractionalFluidTank, gasTank: MultipleFractionalFluidTank) : PurityBasedMultipleFractionalFluidTank(gasTank) {
        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            val thermalFluid = PhysicalFluidManager.getProperties(resource.fluid)
                ?: return 0

            if(thermalFluid.isGaseous) {
                return 0
            }

            return liquidTank.fill(resource, action)
        }

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            val thermalFluid = PhysicalFluidManager.getProperties(resource.fluid)
                ?: return 0.0

            if(thermalFluid.isGaseous) {
                return 0.0
            }

            return liquidTank.fillFractional(resource, action)
        }


    }

    val topFaceHandler = ThermalLayer(this, TopFaceHandler(liquidTank, gasTank))
    val topFaceHandlerLazy: LazyOptional<ThermalLayer<TopFaceHandler>> = LazyOptional.of { topFaceHandler }

    /**
     * Fluid handler for the 4 sides:
     * - Allows extraction of liquids via [PurityBasedMultipleFluidTank]
     * - Allows insertion of liquids
     * - Allows insertion of gas
     * */
    class SideHandler(val liquidTank: MultipleFractionalFluidTank, val gasTank: MultipleFractionalFluidTank) : PurityBasedMultipleFractionalFluidTank(liquidTank) {
        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            val thermalFluid = PhysicalFluidManager.getProperties(resource.fluid)
                ?: return 0

            return if(thermalFluid.isGaseous) {
                gasTank.fill(resource, action)
            }
            else {
                liquidTank.fill(resource, action)
            }
        }

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            val thermalFluid = PhysicalFluidManager.getProperties(resource.fluid)
                ?: return 0.0

            return if(thermalFluid.isGaseous) {
                gasTank.fillFractional(resource, action)
            }
            else {
                liquidTank.fillFractional(resource, action)
            }
        }
    }

    val sideHandler = ThermalLayer(this, SideHandler(liquidTank, gasTank))
    val sideHandlerLazy: LazyOptional<ThermalLayer<SideHandler>> = LazyOptional.of { sideHandler }

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T?> {
        if(cap == ForgeCapabilities.FLUID_HANDLER) {
            if(side != null) {
                return when(side) {
                    Direction.DOWN -> bottomFaceHandlerLazy.cast()
                    Direction.UP -> topFaceHandlerLazy.cast()
                    else -> sideHandlerLazy.cast()
                }
            }
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        bottomFaceHandlerLazy.invalidate()
        topFaceHandlerLazy.invalidate()
        sideHandlerLazy.invalidate()
    }

    override fun getHoverDetail(side: Direction?): Component? {
        val key = when(side) {
            Direction.DOWN -> "hover.eln2.phase_change.bottom"
            Direction.UP -> "hover.eln2.phase_change.top"
            null -> return null
            else -> "hover.eln2.phase_change.side"
        }

        return Component.translatable(key)
    }

    //#endregion

    override fun onCellConnected(actualCell: Cell, remoteCell: Cell) {
        cell.markForRebuild()
        super.onCellConnected(actualCell, remoteCell)
    }

    override fun onCellDisconnected(actualCell: Cell, remoteCell: Cell) {
        cell.markForRebuild()
        super.onCellDisconnected(actualCell, remoteCell)
    }

    /**
     * Voids the tanks.
     * */
    override fun applyWrench(wrench: WrenchItem, context: UseOnContext): InteractionResult {
        liquidTank.fluids.clear()
        gasTank.fluids.clear()

        val body = cell.handle.acquire()
        val temperature = body.temperature
        body.material = PhaseChangeModuleCell.HULL_MATERIAL
        body.mass = PhaseChangeModuleCell.HULL_MASS
        body.temperature = temperature
        cell.handle.release()

        setChanged()

        return InteractionResult.SUCCESS
    }

    /**
     * Recalculates the thermal body's composition and mass.
     * **Assumes already locked!**
     * */
    fun recalculateBody() {
        val body = cell.wire.thermalBody
        val (newMaterial, newMass) = ThermalFluidHandlerHelper.calculateDerivativeMaterialAndMass(PhaseChangeModuleCell.HULL_MASS, PhaseChangeModuleCell.HULL_MATERIAL, getFluids())
        body.material = newMaterial
        body.mass = newMass
        cell.setChanged()
        setChanged()
    }

    //#region Rendering and Sync

    @ClientOnly
    override val clientSidePacketHandlerLazy = createClientSideHandler()

    class RenderState {
        var temperature = 0.0
        var activity = 0.0

        val activitySmoother = FramerateIndependentSmoother1d(0.5)
        var soundInstance: SimpleLoopingBlockEntitySoundInstance<PhaseChangeModuleBlockEntity>? = null
    }

    @ClientOnly
    var renderState: RenderState? = null
        private set

    /**
     * Creates the [renderState] if needed.
     * */
    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(pLevel.isClientSide) {
            renderState = RenderState()
        }
    }

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        handler.withHandler<InternalTemperatureReplicatorBehavior.InternalTemperaturePacket>(InternalTemperatureReplicatorBehavior.InternalTemperaturePacket::deserialize) { packet ->
            renderState!!.temperature = packet.temperature
        }

        handler.withHandler<DistillationActivityReplicatorBehavior.ActivityPacket>(DistillationActivityReplicatorBehavior.ActivityPacket::deserialize) { packet ->
            renderState!!.activity = packet.activity
        }
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState ?: return

        if (state.soundInstance == null) {
            state.soundInstance = SimpleLoopingBlockEntitySoundInstance(this, Eln2Processing.DISTILLATION_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    state.activitySmoother.update(state.activity)
                    it.soundInfo = SoundInfo.distillation(state.activitySmoother.value)
                }

                it.registerOnAudioManager()
            }
        }
    }

    @ServerOnly @OnSimulationThread
    override fun onInternalTemperatureChange(temperature: Quantity<Temperature>) {
        sendBulkPacket(
            InternalTemperatureReplicatorBehavior.InternalTemperaturePacket::serialize,
            InternalTemperatureReplicatorBehavior.InternalTemperaturePacket(!temperature)
        )
    }

    @OnSimulationThread
    override fun onDistillationActivityChange(activity: Double) {
        sendBulkPacket(
            DistillationActivityReplicatorBehavior.ActivityPacket::serialize,
            DistillationActivityReplicatorBehavior.ActivityPacket(activity)
        )
    }

    // onSyncSuggested
    override fun getUpdateTag(): CompoundTag {
        sendBulkPacket(
            InternalTemperatureReplicatorBehavior.InternalTemperaturePacket::serialize,
            InternalTemperatureReplicatorBehavior.InternalTemperaturePacket(!cell.wire.thermalBody.temperature)
        )

        sendBulkPacket(
            DistillationActivityReplicatorBehavior.ActivityPacket::serialize,
            DistillationActivityReplicatorBehavior.ActivityPacket(cell.distillation.activity)
        )

        return super.getUpdateTag()
    }

    //#endregion

    //#region Saving

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put("liquidTank", liquidTank.serializeNBT())
        pTag.put("gasTank", gasTank.serializeNBT())
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        liquidTank.deserializeNBT(pTag.getCompound("liquidTank"))
        gasTank.deserializeNBT(pTag.getCompound("gasTank"))
    }

    //#endregion

    @ServerOnly
    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Graph: ${cell.graph.id}" }

        cell.linkedCells.forEachIndexed { idx, remote ->
            val dir = Direction.from3DDataValue(idx)

            if(remote != null) {
                builder.debugInIDE { "Dir $dir: $remote (${remote.locator.requireLocator(Locators.BLOCK)})" }
            }
        }

        bottomFaceHandler.parent.debugView(builder)
        builder.quantity(cell.wire.thermalBody.temperature)
    }
}

class PhaseChangeModuleBlockEntityVisual(ctx: VisualizationContext, blockEntity: PhaseChangeModuleBlockEntity, partialTick: Float) :
    AbstractBlockEntityVisual<PhaseChangeModuleBlockEntity>(ctx, blockEntity, partialTick),
    SimpleDynamicVisual
{
    val model = (blockState.block as PhaseChangeModuleBlock).model

    val instance: TransformedLightOverrideInstance = visualizationContext.instancerProvider()
        .instancer(FlwInstanceTypes.TRANSFORMED_LIGHT_OVERRIDE, Models.partial(model.modelSupplier.get()))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.center()
            it.rotateToFace(blockState.getValue(HorizontalDirectionalBlock.FACING))
            it.uncenter()
        }

    private var lastTemperature = 0.0

    override fun beginFrame(p0: DynamicVisual.Context?) {
        if(!model.isIncandescent) {
            return
        }

        val targetTemperature = blockEntity.renderState!!.temperature

        if(targetTemperature != lastTemperature) {
            lastTemperature = targetTemperature
            instance.colorWithOverride(ThermalTint.DEFAULT_LIGHT_OVERRIDE, Quantity(targetTemperature, KELVIN))
            instance.setChanged()
        }
    }

    override fun updateLight(p0: Float) {
        relight(instance)
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(instance)
    }

    override fun _delete() {
        instance.delete()
    }
}

/**
 * Consumer for the distillation phase-change activity, used to drive client-side effects like sound.
 * */
fun interface DistillationActivityConsumer {
    fun onDistillationActivityChange(activity: Double)
}

/**
 * Replicates the normalized distillation phase-change activity to the client, where 0 is idle and 1 is maximal boiling and condensation.
 * @param consumer The consumer for the changes.
 * @param supplier The activity supplier.
 * */
class DistillationActivityReplicatorBehavior(val consumer: DistillationActivityConsumer, val supplier: Supplier<Double>) : ReplicatorBehavior {
    var scanInterval = 5
    var scanPhase = SimulationPhase.Pre
    var tolerance = 0.0025

    private var tracked = 0.0

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addSubscriber(SubscriberOptions(scanInterval, scanPhase), this::scan)
    }

    private fun scan(dt: Double, phase: SimulationPhase) {
        var activity = supplier.get()

        if(activity < tolerance) {
            activity = 0.0
        }

        if(activity.approxEq(tracked, tolerance)) {
            return
        }

        tracked = activity
        consumer.onDistillationActivityChange(activity)
    }

    class ActivityPacket(val activity: Double) {
        companion object {
            fun serialize(packet: ActivityPacket, writer: FriendlyByteBuf) {
                writer.writeDouble(packet.activity)
            }

            fun deserialize(reader: FriendlyByteBuf) = ActivityPacket(
                reader.readDouble()
            )
        }
    }
}
