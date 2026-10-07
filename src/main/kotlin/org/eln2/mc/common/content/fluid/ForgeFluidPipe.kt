package org.eln2.mc.common.content.fluid

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.ChatFormatting
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraft.world.level.material.Fluid
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.OptionalDouble
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.*
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.common.content.WrenchInteractable
import org.eln2.mc.common.content.WrenchItem
import org.eln2.mc.common.content.modules.Eln2ForgeFluids
import org.eln2.mc.common.fluids.foundation.FractionalFluidStack
import org.eln2.mc.common.fluids.foundation.IFractionalFluidHandler
import org.eln2.mc.common.fluids.foundation.PhysicalFluidManager
import org.eln2.mc.common.fluids.foundation.IThermalFluidHandler
import org.eln2.mc.common.fluids.foundation.fractional
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3dMask
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.floor
import kotlin.math.min

// P.S. I load all chunks synchronously. It's up to the player to add chunk-loaders, for now...

/**
 * mB/tick of automated pumping.
 * Automatic pumping is added as a convenience feature (and because all pipe mods also have free extraction).
 * I could add a config option that disables it, and add a pump block which needs energy.
 * */
private const val PUMP_RATE = 1
/**
 * Scales [PUMP_RATE] by the fluid's [org.eln2.mc.common.fluids.foundation.PhysicalFluid.gasExpansionFactor], so that gases transfer at an equal liquid-equivalent mass rate to liquids.
 * Falls back to [PUMP_RATE] for non-physical (non-thermal) fluids.
 * */
private fun pumpRateFor(fluid: Fluid): Double {
    val properties = PhysicalFluidManager.getProperties(fluid) ?: return PUMP_RATE.toDouble()
    return PUMP_RATE * properties.gasExpansionFactor
}

/**
 * Gets (ready) the neighbors of [pipe].
 * */
@Suppress("NOTHING_TO_INLINE")
private inline fun getNeighborPipes(pipe: FluidPipeBlockEntity, results: ArrayList<FluidPipeBlockEntity>, exclude: FluidPipeBlockEntity?) {
    val level = pipe.level as ServerLevel

    pipe.whitelist.forEach { dirPipe ->
        val targetBlockPos = pipe.blockPos + dirPipe

        /**
         * This call will load the chunk fully:
         * */
        val neighborBlockEntity = level.getBlockEntity(targetBlockPos) as? FluidPipeBlockEntity
            ?: return@forEach

        if(neighborBlockEntity == exclude) {
            return@forEach
        }

        if(neighborBlockEntity.isReadyForDiscovery() && neighborBlockEntity.whitelist.has(dirPipe.opposite)) {
            results.add(neighborBlockEntity)
        }
    }
}

/**
 * Manages pipe networks for the server. Each level gets a [Repository], which handles allocation of new networks and building the networks based on pipe adjacency.
 * */
@ServerOnly @OnServerThread
object FluidPipeNetworkManager {
    /**
     * Manages networks for a level.
     * */
    class Repository(val level: ServerLevel) {
        /**
         * All [FluidPipeNetwork]s by their unique ID.
         * */
        private val networksByID = HashMap<UUID, FluidPipeNetwork>()

        private fun allocateNetwork() : FluidPipeNetwork {
            val result = FluidPipeNetwork(this, level)
            networksByID.putUnique(result.id, result)
            return result
        }

        fun freeNetwork(network: FluidPipeNetwork) {
            check(networksByID.remove(network.id) != null) {
                DEBUGGER_BREAK()
            }
        }

        private fun isCommonNetwork(neighborList: List<FluidPipeBlockEntity>) : Boolean {
            if(neighborList.size == 1) {
                return true
            }

            val a = neighborList[0].network

            for (i in 1 until neighborList.size) {
                if(a != neighborList[i].network) {
                    return false
                }
            }

            return true
        }

        /**
         * Inserts [pipe] into the world, handling all topological changes and returning the network [pipe] will be part of.
         * The cases are:
         * 1. There are no valid neighbor pipes, so a new network is created.
         * 2. There is only one valid neighbor pipe, so the neighbor's network is joined.
         * 3. There are multiple valid neighbors, but they are part of the same network, so that common network is joined.
         * 4. There are multiple valid neighbors, and there are at least 2 distinct networks. All the distinct networks are destroyed and a new network, with all the pipes from the previous networks and this new pipe is created.
         * */
        fun insertPipe(pipe: FluidPipeBlockEntity) : FluidPipeNetwork {
            val neighborList = ArrayList<FluidPipeBlockEntity>(2)
            getNeighborPipes(pipe, neighborList, null)

            if(neighborList.isEmpty()) {
                /**
                 * Case 1. Create new network:
                 * */
                val network = allocateNetwork()
                network.insert(pipe)
                return network
            }
            else if(isCommonNetwork(neighborList)) {
                /**
                 * Case 2 and 3. Join the existing network:
                 * */
                val network = neighborList[0].network
                network.insert(pipe)
                return network
            }
            else {
                /**
                 * Case 4. We need to create a new network, with all pipes and this one.
                 * */
                val network = allocateNetwork()
                network.insert(pipe)

                /**
                 * Identify separate networks, copy their pipes into the new one and notify pipes:
                 * */
                neighborList.map { it.network }.distinct().forEach { existingNetwork ->
                    existingNetwork.pipes.forEach { relocatedPipe ->
                        network.insert(relocatedPipe)
                    }

                    existingNetwork.pipes.forEach { relocatedPipe ->
                        relocatedPipe.networkChanged(network)
                    }

                    freeNetwork(existingNetwork)
                }

                return network
            }
        }

        /**
         * Removes [pipe] from the world. Can cause topological changes (e.g. splitting the network, if [pipe] is a cut vertex).
         * The cases are:
         * 1. There are no neighbors, so the network is destroyed.
         * 2. There is a single neighbor, so the pipe is removed from the network.
         * 3. There are multiple neighbors, and the pipe isn't a cut vertex, so the pipe is removed from the network.
         * 4. There are multiple neighbors, and the pipe is a cut vertex. The pipe is removed from the network, then the network is split into multiple.
         *
         * Case 3 and 4 are complicated to distinguish without introducing a lot of state, so they are handled by the same scanning routine.
         * */
        fun removePipe(pipe: FluidPipeBlockEntity) {
            val network = pipe.network

            if(network.pipes.size == 1) {
                /**
                 * Case 1. Destroy the network:
                 * */
                freeNetwork(network)
            }
            else {
                val neighborList = ArrayList<FluidPipeBlockEntity>(2)
                getNeighborPipes(pipe, neighborList, null)

                if(neighborList.size == 1) {
                    /**
                     * Case 2. Remove the pipe from the network:
                     * */
                    network.remove(pipe)
                }
                else {
                    /**
                     * Cases 3 and 4. The topology is re-built from scratch.
                     * The algorithm starts a search at each neighbor (excluding the removed pipe) to find each network.
                     * When an original neighbor of the pipe is encountered, it is removed from the list to process.
                     * */

                    val queue = ArrayDeque<FluidPipeBlockEntity>()
                    val visited = HashSet<FluidPipeBlockEntity>()
                    val adjacentNodes = ArrayList<FluidPipeBlockEntity>(6)

                    while (neighborList.isNotEmpty()) {
                        queue.add(neighborList.removeLast())

                        val newNetwork = allocateNetwork()
                        while (queue.isNotEmpty()) {
                            val front = queue.removeFirst()

                            if(!visited.add(front)) {
                                continue
                            }

                            /**
                             * Remove from the neighbor queue:
                             * */
                            neighborList.remove(front)

                            /**
                             * Move the pipe into the network:
                             * */
                            newNetwork.insert(front)
                            front.networkChanged(newNetwork)

                            /**
                             * Enqueue neighbors for processing:
                             * */
                            getNeighborPipes(front, adjacentNodes, pipe)
                            queue.addAll(adjacentNodes)
                            adjacentNodes.clear()
                        }

                        visited.clear()
                    }

                    freeNetwork(network)
                }
            }
        }

        fun update() {
            networksByID.values.forEach {
                it.update()
            }
        }
    }

    private val repositories = HashMap<ServerLevel, Repository>()

    private fun validateUsage() {
        requireIsOnServerThread {
            "Tried to use fluid pipe network manager on non-server thread"
        }
    }

    /**
     * Gets the repository for the level.
     * */
    @OnServerThread
    fun getRepositoryFor(level: ServerLevel): Repository {
        validateUsage()

        var result = repositories[level]

        if(result == null) {
            result = Repository(level)
            repositories.putUnique(level, result)
        }

        return result
    }

    fun update() {
        validateUsage()

        repositories.values.forEach {
            it.update()
        }
    }

    fun clear() {
        validateUsage()

        repositories.clear()
    }
}

/**
 * Transient data structure storing endpoints in a pipe network.
 * The pipe network transports fluids between endpoints, so we don't need to have an internal buffer that needs saving.
 * This just acts as a holder for the endpoint machines, which allows us to run an algorithm to determine the transports each tick.
 *
 * The network building algorithm is very similar to the cell graph.
 * */
@OnServerThread
class FluidPipeNetwork(val repository: FluidPipeNetworkManager.Repository, val level: ServerLevel) {
    val id: UUID = UUID.randomUUID()

    val pipes = HashSet<FluidPipeBlockEntity>()

    /**
     * Fluid handler external to the network. It's a machine, usually.
     * @param capability The remote machine's capability.
     * @param pipe The pipe attached to the machine.
     * @param pipeSide The side of the pipe connected to the machine.
     * */
    data class Endpoint(val capability: IFluidHandler, val pipe: FluidPipeBlockEntity, val pipeSide: Direction)

    /**
     * List of machines connected to a pipe. The network will automatically extract fluid from these machines (pump module).
     * */
    val importEndpointsByPipe = HashMap<FluidPipeBlockEntity, ArrayList<Endpoint>>()

    /**
     * List of machines connected to a pipe. The network will automatically push fluid into these machines.
     * */
    val exportEndpointsByPipe = HashMap<FluidPipeBlockEntity, ArrayList<Endpoint>>()

    /**
     * Inserts the [pipe] and computes the endpoints.
     * */
    fun insert(pipe: FluidPipeBlockEntity) {
        check(pipes.add(pipe)) {
            DEBUGGER_BREAK("Duplicate add to network $pipe")
        }

        invalidateCaches()
        computeEndpoints(pipe)
    }

    /**
     * Updates the colliders for the neighbors of [pipe], ignoring the whitelist.
     * */
    private fun notifyNeighborhood(pipe: FluidPipeBlockEntity) {
        Base6Direction3dMask.FULL.forEach { dir ->
            val targetBlockPos = pipe.blockPos + dir

            if(level.isLoaded(targetBlockPos)) {
                val neighborBlockEntity = level.getBlockEntity(targetBlockPos) as? FluidPipeBlockEntity
                    ?: return@forEach

                neighborBlockEntity.updateColliderVariant()
            }
        }
    }

    /**
     * Removes the [pipe] and all associated data.
     * */
    fun remove(pipe: FluidPipeBlockEntity) {
        check(pipes.remove(pipe)) {
            DEBUGGER_BREAK("Removed non-existent pipe $pipe")
        }

        invalidateCaches()
        importEndpointsByPipe.remove(pipe)
        exportEndpointsByPipe.remove(pipe)

        notifyNeighborhood(pipe)
    }

    /**
     * Re-computes the endpoints for [pipe].
     * */
    fun onNeighborsChanged(pipe: FluidPipeBlockEntity) {
        invalidateCaches()
        computeEndpoints(pipe)
    }

    /**
     * Adds entries for [pipe] into [importEndpointsByPipe] and [exportEndpointsByPipe].
     * */
    private fun computeEndpoints(pipe: FluidPipeBlockEntity) {
        val importEndpoints = importEndpointsByPipe[pipe] ?: ArrayList(6)
        val exportEndpoints = exportEndpointsByPipe[pipe] ?: ArrayList(6)

        importEndpoints.clear()
        exportEndpoints.clear()

        var mask = Base6Direction3dMask.EMPTY

        pipe.whitelist.forEach { dir ->
            val targetPos = pipe.blockPos + dir
            val targetBlockEntity = level.getBlockEntity(targetPos)

            val moduleIndex = dir.get3DDataValue()
            val installedModule = pipe.modules[moduleIndex]

            fun dropModule() {
                if(installedModule != FluidPipeBlockEntity.ModuleType.None) {
                    pipe.modules[moduleIndex] = FluidPipeBlockEntity.ModuleType.None

                    if(installedModule.item != null){
                        level.addItem(
                            pipe.blockPos.x + 0.5,
                            pipe.blockPos.y + 0.5,
                            pipe.blockPos.z + 0.5,
                            ItemStack(installedModule.item.get(), 1)
                        )
                    }

                    pipe.setChanged()
                    pipe.onModuleChanged()
                }
            }

            if(targetBlockEntity == null) {
                /**
                 * The machine disappeared, drop the module:
                 * */
                dropModule()
                return@forEach
            }

            if(targetBlockEntity is FluidPipeBlockEntity) {
                /**
                 * Modules won't be allowed to exist between pipes because it'd be weird:
                 * */
                dropModule()
                mask += dir
                return@forEach
            }

            val capabilityLazy = targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.opposite)

            if(!capabilityLazy.isPresent) {
                /**
                 * If the machine doesn't have a capability, drop the module as an item:
                 * */
                dropModule()
                return@forEach
            }

            mask += dir

            val endpoint = Endpoint(capabilityLazy.resolve().get(), pipe, dir)

            if(installedModule.networkImportsFromEndpoint) {
                importEndpoints.add(endpoint)
            }

            if(installedModule.networkExportsToEndpoint) {
                exportEndpoints.add(endpoint)
            }
        }

        /**
         * Trims empty endpoint lists:
         * */

        if(importEndpoints.isNotEmpty()) {
            importEndpointsByPipe.putIfAbsent(pipe, importEndpoints)
        }
        else {
            importEndpointsByPipe.remove(pipe)
        }

        if(exportEndpoints.isNotEmpty()) {
            exportEndpointsByPipe.putIfAbsent(pipe, exportEndpoints)
        }
        else {
            exportEndpointsByPipe.remove(pipe)
        }

        pipe.updateColliderVariant()
        notifyNeighborhood(pipe)
    }

    /**
     * Checks if any endpoints are accepting the fluid.
     * */
    fun canImport(fluid: FluidStack) : Boolean {
        /**
         * P.S. Iterating the jagged array shouldn't be an issue, we expect like 10 of those endpoints to exist per network.
         * */
        for (endpoints in importEndpointsByPipe.values) {
            for (endpoint in endpoints) {
                for (i in 0 until endpoint.capability.tanks) {
                    if(endpoint.capability.isFluidValid(i, fluid)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * List of target machines sorted by distance (along pipes) from the key. They order is mutated in the [fillDiscrete] call though.
     * */
    val endpointsCache = HashMap<FluidPipeBlockEntity, ArrayList<Endpoint>>()

    /**
     * Invalidates all caches. Called when pipes are added/removed and when endpoints are added/removed.
     * */
    private fun invalidateCaches() {
        endpointsCache.clear()
    }

    /**
     * Gets the endpoints with access to [source].
     * */
    private fun getEndpointList(source: FluidPipeBlockEntity) = endpointsCache.computeIfAbsent(source) {
        val result = ArrayList<Endpoint>()

        val neighborPipes = ArrayList<FluidPipeBlockEntity>(6)
        val visited = HashSet<FluidPipeBlockEntity>()
        val queue = ArrayDeque<FluidPipeBlockEntity>()

        queue.add(source)

        while (queue.isNotEmpty()) {
            val front = queue.remove()

            if(!visited.add(front)) {
                continue
            }

            val exportEndpoints = exportEndpointsByPipe[front]

            if(exportEndpoints != null) {
                for (i in exportEndpoints.indices) {
                    val exportEndpoint = exportEndpoints[i]

                    result.add(exportEndpoint)
                }
            }

            getNeighborPipes(front, neighborPipes, null)
            queue.addAll(neighborPipes)
            neighborPipes.clear()
        }

        result
    }

    /**
     * Called to move fluid from an endpoint to the rest of the endpoints by using [fillFused].
     * Tries to distribute evenly to all endpoints.
     * @param sourcePipe The pipe attached to the endpoint.
     * @param sourceSide The side of [sourcePipe] [resource] came from (used to prevent inserting the resource back).
     * */
    fun fillDiscrete(sourcePipe: FluidPipeBlockEntity, sourceSide: Direction, resource: FluidStack, action: IFluidHandler.FluidAction) : Int {
        if(resource.isEmpty) {
            return 0
        }

        val fractional = resource.fractional()

        /**
         * Simulates a fill to get the fractional amount we can transport:
         * */
        val simulation = fractional.copyWithAmount(
            fillFused(
                sourcePipe,
                sourceSide,
                fractional,
                OptionalDouble.EMPTY,
                IFluidHandler.FluidAction.SIMULATE
            )
        )

        /**
         * Gets the integer amount we can transport:
         * */
        val quantizedSimulation = simulation.quantized()

        if(quantizedSimulation.amount == 0) {
            /**
             * Can't transport anything:
             * */
            return 0
        }

        if(action == IFluidHandler.FluidAction.EXECUTE) {
            /**
             * Execute fill with the amount closest to the target:
             * */
            val filled = fillFused(
                sourcePipe,
                sourceSide,
                quantizedSimulation.fractional(),
                OptionalDouble.EMPTY,
                IFluidHandler.FluidAction.EXECUTE
            )

            if(!filled.approxEq(quantizedSimulation.amount.toDouble(), FractionalFluidStack.EPSILON)) {
                LOG.error(DEBUGGER_BREAK("Did not fill expected amount: $filled, ${quantizedSimulation.amount}"))
            }
        }

        return quantizedSimulation.amount
    }

    private var fillFractionalSimulationTable = Object2DoubleOpenHashMap<IFluidHandler>().also {
        it.defaultReturnValue(0.0)
    }

    /**
     * Called to move fluid from an endpoint to the rest of the endpoints.
     * Tries to distribute evenly to all endpoints. Of course, if there exist discrete handlers, it won't be completely fair.
     *
     * Algorithm description:
     * The algorithm tries to distribute [resource] evenly to all handlers, regardless of:
     * - Their current capacity (how much of the resource they can accept)
     * - If they are fractional or discrete handlers
     *
     * The simplest possible case would be if all handlers are fractional, and they all have infinite capacity. Then, each one could receive [resource]` / count` fluid.
     * But generally, we can have handlers which are full or almost full (and they cannot accept all of [resource]` / count`), and we can also have discrete handlers.
     * To start with, we simulate inserting [resource] (all of it) into all handlers. If [resource] is less than `1mB`, we completely skip the discrete handlers.
     * Anyway, we store the results in [fillFractionalSimulationTable] if the filled amount is larger than [FractionalFluidStack.EPSILON]. We also increment a counter of valid candidates if that's true.
     * After this, we get the list of endpoints for [sourcePipe], and we sort it *in-place* (from small to large) by comparing the values in [fillFractionalSimulationTable] that we just prepared.
     * From moment to moment, the order doesn't actually change, so this is usually an `O(n)` operation.
     * Then, we go through the list (looking only at valid handlers), and we calculate their share of the resource by ` min(remainingAmount / remainingCandidates, capacity)`, where `remainingAmount` was initially [resource], `remainingCandidates` was initially the number of valid candidates, and `capacity` is the value from the [fillFractionalSimulationTable].
     * If the handler is integer, we quantize this result.
     * If the result is larger than [FractionalFluidStack.EPSILON], we subtract the result from the `remainingAmount`, and we execute the transfer.
     * At the end, we decrement the remaining candidates.
     * @param sourcePipe The pipe attached to the endpoint.
     * @param sourceSide The side of [sourcePipe] [resource] came from (used to prevent inserting the resource back).
     * */
    fun fillFused(sourcePipe: FluidPipeBlockEntity, sourceSide: Direction, resource: FractionalFluidStack, resourceTemperature: OptionalDouble, action: IFluidHandler.FluidAction) : Double {
        if (resource.isEmpty) {
            return 0.0
        }

        val endpointList = getEndpointList(sourcePipe)

        if (endpointList.isEmpty()) {
            return 0.0
        }

        try {
            val quantizedResource = resource.quantized()
            var remainingCandidates = 0

            /**
             * Simulates inserting the [resource] into all handlers. This just gives us the upper bound on how much fluid they can accept.
             * */
            endpointList.forEach { destinationEndpoint ->
                val handler = destinationEndpoint.capability

                val simulatedFill = if(destinationEndpoint.pipe == sourcePipe && destinationEndpoint.pipeSide == sourceSide) {
                    0.0
                }
                else {
                    if(handler is IFractionalFluidHandler) {
                        if(handler is IThermalFluidHandler) {
                            handler.fillThermal(resource, resourceTemperature, IFluidHandler.FluidAction.SIMULATE)
                        }
                        else {
                            handler.fillFractional(resource, IFluidHandler.FluidAction.SIMULATE)
                        }
                    }
                    else {
                        if (quantizedResource.amount > 0) {
                            handler.fill(quantizedResource, IFluidHandler.FluidAction.SIMULATE).toDouble()
                        }
                        else {
                            /**
                             * Skip discrete handlers completely since the resource is less than 1mB:
                             * */
                            0.0
                        }
                    }
                }

                if(simulatedFill >= FractionalFluidStack.EPSILON) {
                    remainingCandidates++
                    fillFractionalSimulationTable.put(handler, simulatedFill)
                }
            }

            if(remainingCandidates == 0) {
                return 0.0
            }

            /**
             * Sorts handlers from the smallest capacity to the largest capacity.
             * This ensures small tanks fill up, but the amount they rejected still can go to the large tanks fairly.
             * */
            endpointList.sortWith { a, b ->
                fillFractionalSimulationTable.getDouble(a.capability).compareTo(fillFractionalSimulationTable.getDouble(b.capability))
            }

            var remainingAmount = resource.amount
            var totalFilled = 0.0

            for (destinationEndpoint in endpointList) {
                val handler = destinationEndpoint.capability
                /**
                 * Remove so it doesn't get double-counted:
                 * */
                val capacity = fillFractionalSimulationTable.removeDouble(handler)

                if (capacity < FractionalFluidStack.EPSILON) {
                    continue
                }

                var transfer = min(remainingAmount / remainingCandidates, capacity)

                /**
                 * If the handler is discrete, we will quantize it:
                 * */
                if (handler !is IFractionalFluidHandler) {
                    transfer = floor(transfer)
                }

                if (transfer >= FractionalFluidStack.EPSILON) {
                    if (action == IFluidHandler.FluidAction.EXECUTE) {
                        if (handler is IFractionalFluidHandler) {
                            if(handler is IThermalFluidHandler) {
                                handler.fillThermal(resource.copyWithAmount(transfer), resourceTemperature, IFluidHandler.FluidAction.EXECUTE)
                            }
                            else {
                                handler.fillFractional(resource.copyWithAmount(transfer), IFluidHandler.FluidAction.EXECUTE)
                            }
                        }
                        else {
                            handler.fill(FluidStack(resource.fluid, transfer.toInt()), IFluidHandler.FluidAction.EXECUTE)
                        }
                    }

                    totalFilled += transfer
                    remainingAmount -= transfer
                }

                remainingCandidates--
            }

            return totalFilled
        }
        finally {
            fillFractionalSimulationTable.clear()
        }
    }

    /**
     * Tries to extract fluid from the endpoints and push it into the network.
     * The algorithm applied for every endpoint (source) works as follows:
     * - An extraction of [PUMP_RATE] is simulated from the source
     * - A network fill is simulated with that result, to obtain the amount that can be filled
     * - The amount that can be filled is extracted from the endpoint
     * - The result of that extraction is then network filled
     *
     * This prevents mass duplication but will allow voiding if handler logic is bad.
     * */
    private fun executePump(pipe: FluidPipeBlockEntity, endpoints: ArrayList<Endpoint>) {
        for (sourceEndpoint in endpoints) {
            val fluidSource = sourceEndpoint.capability
            val sourceSide = sourceEndpoint.pipeSide

            /**
             * Fused logic for both dumb sources and thermal sources.
             * */
            if(fluidSource is IFractionalFluidHandler) {
                val pumpRate = pumpRateFor(fluidSource.getFractionalFluidInTank(0).fluid)

                val drainSimulation: FractionalFluidStack
                val drainSimulationTemperature: OptionalDouble

                if(fluidSource is IThermalFluidHandler) {
                    val thermalStack = fluidSource.drainThermal(
                        pumpRate,
                        IFluidHandler.FluidAction.SIMULATE
                    ) ?: continue

                    drainSimulation = thermalStack.packet
                    drainSimulationTemperature = OptionalDouble.wrap(thermalStack.temperature)
                }
                else {
                    drainSimulation = fluidSource.drainFractional(pumpRate, IFluidHandler.FluidAction.SIMULATE)
                    drainSimulationTemperature = OptionalDouble.EMPTY
                }

                if(drainSimulation.isEmpty) {
                    continue
                }

                val fillSimulation = fillFused(
                    pipe,
                    sourceSide,
                    drainSimulation,
                    drainSimulationTemperature,
                    IFluidHandler.FluidAction.SIMULATE
                )

                if(fillSimulation < FractionalFluidStack.EPSILON) {
                    continue
                }

                val drain: FractionalFluidStack
                val drainTemperature: OptionalDouble

                if(fluidSource is IThermalFluidHandler) {
                    val thermalStack = fluidSource.drainThermal(
                        FractionalFluidStack(drainSimulation.fluid, fillSimulation),
                        IFluidHandler.FluidAction.EXECUTE
                    )

                    if(thermalStack == null) {
                        LOG.error(DEBUGGER_BREAK("Thermal handler didn't yield anything on execute, but simulation did yield: $fluidSource"))
                        continue
                    }

                    drain = thermalStack.packet
                    drainTemperature = OptionalDouble.wrap(thermalStack.temperature)

                    if(drainTemperature != drainSimulationTemperature) {
                        /**
                         * We take exact equality here, based on our implementation.
                         * */
                        LOG.error(DEBUGGER_BREAK("Thermal handler simulation temperature is not equal to execution temperature: $drainSimulationTemperature, $drainTemperature"))
                    }
                }
                else {
                    drain = fluidSource.drainFractional(
                        FractionalFluidStack(drainSimulation.fluid, fillSimulation),
                        IFluidHandler.FluidAction.EXECUTE
                    )

                    if(drain.isEmpty) {
                        LOG.error(DEBUGGER_BREAK("Fractional handler didn't yield anything on execute, but simulation did yield: $fluidSource"))
                        continue
                    }

                    drainTemperature = OptionalDouble.EMPTY
                }

                val filled = fillFused(
                    pipe,
                    sourceSide,
                    drain,
                    drainTemperature,
                    IFluidHandler.FluidAction.EXECUTE
                )

                if(!filled.approxEq(drain.amount, FractionalFluidStack.EPSILON)) {
                    // Can't really get much information in the logs, it concerns the entire network
                    LOG.error(DEBUGGER_BREAK("Fused fill did not match drained amount (from $fluidSource): ${drain.amount}, $filled"))
                }
            }
            /**
             * Non-thermal source (thermal handlers are, by definition, fractional):
             * */
            else {
                val pumpRate = pumpRateFor(fluidSource.getFluidInTank(0).fluid).toInt()

                if(pumpRate <= 0) {
                    continue
                }

                val drainSimulation = fluidSource.drain(pumpRate, IFluidHandler.FluidAction.SIMULATE)

                if(drainSimulation.isEmpty) {
                    continue
                }

                val fillSimulation = fillDiscrete(
                    pipe,
                    sourceSide,
                    drainSimulation,
                    IFluidHandler.FluidAction.SIMULATE
                )

                if(fillSimulation <= 0) {
                    continue
                }

                val drain = fluidSource.drain(
                    FluidStack(drainSimulation.fluid, fillSimulation),
                    IFluidHandler.FluidAction.EXECUTE
                )

                if(drain.isEmpty) {
                    LOG.error(DEBUGGER_BREAK("Discrete handler didn't yield anything on execute, but simulation did yield: $fluidSource"))
                    continue
                }

                val filled = fillDiscrete(
                    pipe,
                    sourceSide,
                    drain,
                    IFluidHandler.FluidAction.EXECUTE
                )

                if(filled != drain.amount) {
                    // Can't really get much information in the logs, it concerns the entire network
                    LOG.error(DEBUGGER_BREAK("Discrete fill did not match drained amount ($fluidSource): ${drain.amount}, $filled"))
                }
            }
        }
    }

    private val pumpPairs = ArrayList<Map.Entry<FluidPipeBlockEntity, ArrayList<Endpoint>>>()
    private val updateHandlers = ArrayList<Endpoint>()

    /**
     * Applies all automatic fluid transport, created by the pump module.
     * */
    fun update() {
        /**
         * I'm cloning the collections because it actually doesn't work without.
         * I think me filling the mekanism tank causes a block update which triggers an endpoint re-compute on my end, which caues the error.
         * */
        try {
            val pumpPairs = pumpPairs
            val updateHandlers = updateHandlers

            importEndpointsByPipe.forEach { entry: Map.Entry<FluidPipeBlockEntity, ArrayList<Endpoint>> ->
                pumpPairs.add(entry)
            }

            for (pairIndex in pumpPairs.indices) {
                val entry = pumpPairs[pairIndex]
                val pipe = entry.key
                val source = entry.value

                for (endpointIndex in source.indices) {
                    updateHandlers.add(entry.value[endpointIndex])
                }

                executePump(pipe, updateHandlers)

                updateHandlers.clear()
            }
        }
        finally {
            updateHandlers.clear()
            pumpPairs.clear()
        }
    }

    /**
     * Removes the pipe from the data structures, but doesn't trigger a rebuild of the graph.
     * Used when a chunk unloads.
     */
    fun unloadPipe(pipe: FluidPipeBlockEntity) {
        if (!pipes.remove(pipe)) {
            LOG.error("Pipe $pipe (${pipe.blockPos}) was not in network $id during unloading")
        }

        invalidateCaches()
        importEndpointsByPipe.remove(pipe)
        exportEndpointsByPipe.remove(pipe)

        if(pipes.isEmpty()) {
            repository.freeNetwork(this)
        }
    }
}

class FluidPipeBlock : Block(eln2StandardBlockProperties().noOcclusion().dynamicShape()), EntityBlock {
    companion object {
        /**
         * Can't encode e.g. the direction mask as an integer property.
         * We need this bullshit for the JSON mojang model:
         * */
        val PROPERTIES = Array<BooleanProperty>(6) { dataValue ->
            when(Direction.from3DDataValue(dataValue)) {
                Direction.DOWN -> BooleanProperty.create("down")
                Direction.UP ->  BooleanProperty.create("up")
                Direction.NORTH -> BooleanProperty.create("north")
                Direction.SOUTH ->  BooleanProperty.create("south")
                Direction.WEST -> BooleanProperty.create("west")
                Direction.EAST -> BooleanProperty.create("east")
            }
        }

        val CENTER: VoxelShape = box(7.0, 7.0, 7.0, 9.0, 9.0, 9.0)

        val PIPES: Map<Direction, VoxelShape> = mapOf(
            Direction.NORTH to box(7.0, 7.0, 0.0, 9.0, 9.0, 7.0),
            Direction.SOUTH to box(7.0, 7.0, 9.0, 9.0, 9.0, 16.0),
            Direction.WEST  to box(0.0, 7.0, 7.0, 7.0, 9.0, 9.0),
            Direction.EAST  to box(9.0, 7.0, 7.0, 16.0, 9.0, 9.0),
            Direction.DOWN  to box(7.0, 0.0, 7.0, 9.0, 7.0, 9.0),
            Direction.UP    to box(7.0, 9.0, 7.0, 9.0, 16.0, 9.0)
        )

        val COLLIDERS = Base6Direction3dMask.ALL_MASKS.associateWith { mask ->
            var result = CENTER

            mask.forEach { dir ->
                result = Shapes.or(result, PIPES[dir]!!)
            }

            result.optimize()
            result
        }.let { map ->
            Array(Base6Direction3dMask.FULL.value + 1) { idx ->
                map[Base6Direction3dMask(idx)] ?: error(DEBUGGER_BREAK("Could not match mask $idx"))
            }
        }

        fun getMask(state: BlockState): Base6Direction3dMask {
            var result = Base6Direction3dMask.EMPTY

            for (i in 0 until 6) {
                val property = PROPERTIES[i]

                if(state.getValue(property)) {
                    result += Direction.from3DDataValue(i)
                }
            }

            return result
        }

        fun getCollider(state: BlockState) : VoxelShape {
            val mask = getMask(state)

            return COLLIDERS[mask.value]
        }
    }

    val blockStates: Array<BlockState>

    init {
        val defaultState = let {
            var result = getStateDefinition().any()

            PROPERTIES.forEach {
                result = result.setValue(it, false)
            }

            result
        }

        registerDefaultState(defaultState)

        blockStates = Array(Base6Direction3dMask.FULL.value + 1) { idx ->
            var blockState = defaultState

            Base6Direction3dMask(idx).forEach { dir ->
                val property = PROPERTIES[dir.get3DDataValue()]

                blockState = blockState.setValue(property, true)
            }

            blockState
        }
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(pBuilder)

        PROPERTIES.forEach {
            pBuilder.add(it)
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onRemove(pState: BlockState, pLevel: Level, pPos: BlockPos, pNewState: BlockState, pIsMoving: Boolean) {
        if (!pState.`is`(pNewState.block)) {
            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving)
        }
    }

    //#region Collider

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getCollider(pState)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getCollider(pState)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getVisualShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getCollider(pState)

    //#endregion

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = FluidPipeBlockEntity(pPos, pState)

    /**
     * Schedules the [FluidPipeBlockEntity.neighborChanged] to execute on the server.
     * I scheduled it because I'm not sure if it executes after everything has cleaned up or before, so meh.
     * */
    override fun onNeighborChange(state: BlockState, level: LevelReader, pos: BlockPos, neighbor: BlockPos) {
        super.onNeighborChange(state, level, pos, neighbor)

        if(!level.isClientSide) {
            val blockEntity = level.getBlockEntity(pos) as? FluidPipeBlockEntity
                ?: return

            blockEntity.neighborChanged()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ): InteractionResult {
        if(pHand != InteractionHand.MAIN_HAND) {
            return InteractionResult.FAIL
        }

        if(pPlayer.mainHandItem.item !is FluidPipeModuleItem && !pPlayer.mainHandItem.isEmpty) {
            return InteractionResult.FAIL
        }

        val blockEntity = pLevel.getBlockEntity(pPos) as? FluidPipeBlockEntity
            ?: return InteractionResult.FAIL

        if(pLevel.isClientSide) {
            return InteractionResult.SUCCESS
        }

        return blockEntity.moduleInteraction(pPlayer)
    }
}

class FluidPipeModuleItem : Item(Properties())
{
    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)
        pTooltipComponents.add(Component.translatable("tooltip.eln2.fluid_pipe_module.use").withStyle(ChatFormatting.GRAY))
    }
}

class FluidPipeBlockEntity(pPos: BlockPos, pState: BlockState) : BlockEntity(Eln2ForgeFluids.FLUID_PIPE_BLOCK_ENTITY.get(), pPos, pState), WrenchInteractable, ComponentDisplay {
    //#region Block Entity Lifetime Hooks

    /**
     * Acquires the repository and registers the pipe into the network, if on the server.
     *
     * **P.S.** It seems [onLoad] is much safer than [setLevel].**
     * I had logic that ran on insertion which eventually lead to trying to `getBlockEntity(...)` the block entity that was being inserted on `setLevel`, and it was breaking the game completely.
     * */
    override fun onLoad() {
        super.onLoad()

        if(level?.isClientSide == false) {
            val level = level as? ServerLevel
                ?: error(DEBUGGER_BREAK("Could not cast level to ServerLevel for pipe"))

            repositoryInternal = FluidPipeNetworkManager.getRepositoryFor(level)
            registerIntoNetwork()
        }
    }

    override fun setRemoved() {
        if(level?.isClientSide == false) {
            removeFromNetwork()
        }

        super.setRemoved()
    }

    override fun onChunkUnloaded() {
        if(level?.isClientSide == false) {
            if(networkInternal != null) {
                networkInternal!!.unloadPipe(this)
                networkInternal = null
            }
        }

        super.onChunkUnloaded()
    }

    //#endregion

    private var repositoryInternal : FluidPipeNetworkManager.Repository? = null
    val repository: FluidPipeNetworkManager.Repository get() = repositoryInternal ?: error(DEBUGGER_BREAK("Tried to get repository for pipe at $blockPos before it was acquired"))

    private var networkInternal: FluidPipeNetwork? = null
    val network: FluidPipeNetwork get() = networkInternal ?: error(DEBUGGER_BREAK("Tried to get network for pipe at $blockPos before it was acquired"))

    /**
     * Checks if this block entity has had [onLoad] called and is ready for discovery.
     * */
    fun isReadyForDiscovery(): Boolean = repositoryInternal != null

    /**
     * Module item installed on the connection to a machine.
     * @param item The item corresponding to the module, or null, for the default value.
     * @param networkImportsFromEndpoint True if the network actively pulls from the machine.
     * @param networkExportsToEndpoint True if the network actively pushes into the machine.
     * @param machineCanPushIntoNetwork True if the machine is allowed to push into the network.
     * */
    enum class ModuleType(val item: RegistryObject<FluidPipeModuleItem>?, val networkImportsFromEndpoint: Boolean, val networkExportsToEndpoint: Boolean, val machineCanPushIntoNetwork: Boolean, val modelSupplier: Supplier<PartialModel>?) {
        /**
         * The default value. Both pushes and pulls.
         * - The network tries to pull fluid from the machine.
         * - The network tries to push fluid into the machine.
         * */
        None(
            null,
            true,
            true,
            true,
            null
        ),

        /**
         * Gate that prevents the machine from receiving fluid from the network.
         * - The network tries to pull fluid from the machine.
         * - The network doesn't push fluid into the machine.
         * */
        ExtractionValve(
            Eln2ForgeFluids.FLUID_PIPE_EXTRACTION_VALVE,
            true,
            false,
            true,
            { FlwModels.FLUID_PIPE_IMPORT_GATE_MODULE }
        ),

        /**
         * Gate that prevents the network from receiving fluid from the machine.
         * - The network tries to push fluid into the machine.
         * - The network doesn't pull fluid from the machine.
         * */
        InsertionValve(
            Eln2ForgeFluids.FLUID_PIPE_INSERTION_VALVE,
            false,
            true,
            false,
            { FlwModels.FLUID_PIPE_EXPORT_GATE_MODULE }
        )
    }

    /**
     * Whitelist of sides that allow pipe-pipe connections.
     * */
    var whitelist = Base6Direction3dMask.FULL
        private set

    /**
     * The modules applied to each side of the pipe.
     * */
    var modules = Array<ModuleType>(6) {
        ModuleType.None
    }

    //#region Capability

    /**
     * Capability for a specific side. The side basically decides if `fill` is allowed based on the installed module.
     * */
    class Handler(val pipe: FluidPipeBlockEntity, val side: Direction) : IThermalFluidHandler {
        //#region Dummy Implementation

        /**
         * Indicates that there is a tank to work with:
         * */
        override fun getTanks() = 1

        /**
         * Related to drain methods. Shows that draining is not possible:
         * */
        override fun getFluidInTank(tank: Int): FluidStack = FluidStack.EMPTY
        override fun getFractionalFluidInTank(tank: Int) = FractionalFluidStack.EMPTY

        /**
         * Indicates that there is space for insert:
         * */
        override fun getTankCapacity(tank: Int) = 1000
        override fun getFractionalTankCapacity(tank: Int) = 1000.0

        /**
         * Pipes don't allow the machines to pull from the network. The network pushes fluid into the machines.
         * */
        override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY
        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY
        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drainThermal(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = null
        override fun drainThermal(maxDrain: Double, action: IFluidHandler.FluidAction) = null

        //#endregion

        val module get() = pipe.modules[side.get3DDataValue()]

        /**
         * Checks if the fluid is valid for any handler. Also makes sure the machine is allowed to push.
         * */
        override fun isFluidValid(tank: Int, stack: FluidStack) : Boolean {
            if(!module.machineCanPushIntoNetwork) {
                return false
            }

            return pipe.network.canImport(stack)
        }

        /**
         * If the machine is allowed to push, forwards the action to the network.
         * */
        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            if(!module.machineCanPushIntoNetwork) {
                return 0
            }

            return pipe.network.fillDiscrete(pipe, side, resource, action)
        }

        /**
         * If the machine is allowed to push, forwards the action to the network.
         * */
        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            if(!module.machineCanPushIntoNetwork) {
                return 0.0
            }

            return pipe.network.fillFused(pipe, side, resource, OptionalDouble.EMPTY, action)
        }

        override fun fillThermal(resource: FractionalFluidStack, temperature: OptionalDouble, action: IFluidHandler.FluidAction): Double {
            if(!module.machineCanPushIntoNetwork) {
                return 0.0
            }

            return pipe.network.fillFused(pipe, side, resource, temperature, action)
        }
    }

    var handlers = Array<Handler>(6) {
        Handler(this, Direction.from3DDataValue(it))
    }

    var handlersLazy = Array<LazyOptional<Handler>>(6) {
        LazyOptional.of { handlers[it] }
    }

    override fun <T> getCapability(cap: Capability<T?>, side: Direction?): LazyOptional<T?> {
        if(side != null) {
            return handlersLazy[side.get3DDataValue()].cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()

        handlersLazy.forEach {
            it.invalidate()
        }
    }

    //#endregion

    //#region Interaction

    /**
     * @param pipe The clicked pipe or null if the center was clicked.
     * */
    private class PickResult(val pipe: Direction?)

    private fun pick(player: Player): PickResult? {
        val mask = FluidPipeBlock.getMask(blockState)

        val pipeObjects = FluidPipeBlock.PIPES
            .filter { (dir, _) -> mask.has(dir) }
            .flatMap { (dir, shape) ->
                shape.toBoxList().map { box ->
                    dir to box.move(blockPos)
                }
            }

        val centerObjects = FluidPipeBlock.CENTER.toBoxList().map { box ->
            null to box.move(blockPos)
        }

        val objects = pipeObjects.plus(centerObjects)

        val pick = clipScene(player, { (_, aabb) -> aabb }, objects)
            ?: return null

        return PickResult(pick.first)
    }

    /**
     * Inserts a module or removes a module.
     * */
    @OnServerThread
    fun moduleInteraction(pPlayer: Player) : InteractionResult {
        requireIsOnServerThread()

        val picked = pick(pPlayer)
            ?: return InteractionResult.FAIL

        if(picked.pipe == null) {
            /**
             * Ignored:
             * */
            return InteractionResult.FAIL
        }

        val existingModule = modules[picked.pipe.get3DDataValue()]
        val itemInHand = pPlayer.mainHandItem

        fun removeModule() : Boolean {
            if(existingModule == ModuleType.None) {
                return false
            }

            if(existingModule.item != null) {
                if(!pPlayer.addItem(ItemStack(existingModule.item.get(), 1))) {
                    return false
                }
            }

            removeFromNetwork()
            modules[picked.pipe.get3DDataValue()] = ModuleType.None
            registerIntoNetwork()

            onModuleChanged()

            return true
        }

        /**
         * Remove the installed module:
         * */
        if(itemInHand.item == null || itemInHand.isEmpty) {
            if(!removeModule()) {
                return InteractionResult.FAIL
            }

            return InteractionResult.SUCCESS
        }
        /**
         * Try to install a module:
         * */
        else {
            val targetModuleType = ModuleType.entries.firstOrNull { module ->
                module.item?.get() == itemInHand.item
            }

            if(targetModuleType == null) {
                /**
                 * Weird, we checked in the block handler if it's a good item.
                 * */
                LOG.error("Could not get pipe module type for ${itemInHand.item}")
                return InteractionResult.FAIL
            }

            removeModule()

            removeFromNetwork()
            modules[picked.pipe.get3DDataValue()] = targetModuleType
            registerIntoNetwork()

            itemInHand.eln2Consume(pPlayer)
            onModuleChanged()

            return InteractionResult.CONSUME
        }
    }

    /**
     * Called when a module changed, to sync.
     * */
    @OnServerThread
    fun onModuleChanged() {
        setChanged()
        this.setSyncDirty()
    }

    /**
     * Removes a direction from the whitelist.
     * */
    override fun applyWrench(wrench: WrenchItem, context: UseOnContext): InteractionResult {
        val player = context.player
            ?: return InteractionResult.FAIL

        if(context.level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val picked = pick(player)
            ?: return InteractionResult.FAIL

        /**
         * If we clicked the center, we will bring back a direction if it's not in the whitelist:
         * */
        if(picked.pipe == null) {
            val face = context.clickedFace

            if (whitelist.has(face)) {
                /**
                 * Ignored:
                 * */
                return InteractionResult.FAIL
            }

            removeFromNetwork()
            whitelist += face
            registerIntoNetwork()
            setChanged()

            return InteractionResult.SUCCESS
        }

        /**
         * If we clicked a pipe, we will remove the direction from the whitelist:
         * */
        if(!whitelist.has(picked.pipe)) {
            /**
             * Ignored:
             * */
            return InteractionResult.FAIL
        }

        removeFromNetwork()
        whitelist -= picked.pipe
        registerIntoNetwork()

        setChanged()

        return InteractionResult.SUCCESS
    }

    //#endregion

    /**
     * Called when the block entity is added into the world.
     * */
    @OnServerThread
    private fun registerIntoNetwork() {
        check(networkInternal == null) {
            DEBUGGER_BREAK("Tried to register pipe that was already in a network")
        }

        networkInternal = repository.insertPipe(this)
    }

    /**
     * Called when the block entity is removed from the world.
     * */
    @OnServerThread
    private fun removeFromNetwork() {
        if(networkInternal != null) {
            repository.removePipe(this)
            networkInternal = null
        }
    }

    /**
     * Called when a neighbor has changed, and the connections may need to be updated.
     * Called by the scheduler, after the block receives the event (see the call site).
     *
     * This can fire before [onLoad] has run: a neighbor's [BlockEntity.setChanged] fans out via [Level.updateNeighbourForOutputSignal] to any neighbor in a FULL-loaded chunk, including pipes whose [registerIntoNetwork] hasn't executed yet.
     * In that case, the pipe isn't in any network, so there is nothing to invalidate; [registerIntoNetwork] will run [org.eln2.mc.common.content.fluid.FluidPipeNetwork.computeEndpoints] over the current neighbor state when [onLoad] fires.
     * */
    @OnServerThread
    fun neighborChanged() {
        if(networkInternal == null) {
            return
        }

        network.onNeighborsChanged(this)
    }

    /**
     * Called by the manager when topological changes have occurred.
     * */
    @OnServerThread
    fun networkChanged(newNetwork: FluidPipeNetwork) {
        check(repositoryInternal != null) {
            DEBUGGER_BREAK()
        }
        networkInternal = newNetwork
    }

    /**
     * Called by the network to update the collider variant.
     * */
    @OnServerThread
    fun updateColliderVariant() {
        var mask = Base6Direction3dMask.EMPTY

        whitelist.forEach { dir ->
            val targetBlockEntity = level!!.getBlockEntity(blockPos + dir)
                ?: return@forEach

            if(targetBlockEntity is FluidPipeBlockEntity) {
                if(targetBlockEntity.isReadyForDiscovery()) {
                    if(targetBlockEntity.whitelist.has(dir.opposite)) {
                        mask += dir
                    }
                }
            }
            else {
                if(targetBlockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.opposite).isPresent) {
                    mask += dir
                }
            }
        }

        val block = blockState.block as FluidPipeBlock
        val targetBlockState = block.blockStates[mask.value]

        if(targetBlockState != blockState) {
            level!!.setBlock(
                blockPos,
                targetBlockState,
                Block.UPDATE_ALL
            )
        }
    }

    //#region Saving and Sync

    private fun saveToTag(pTag: CompoundTag) {
        pTag.putBase6Direction3dMask("pipeWhitelist", whitelist)
        pTag.putIntArray("modules", modules.map { it.ordinal })
    }

    private fun loadFromTag(pTag: CompoundTag) {
        whitelist = pTag.getBase6Direction3dMask("pipeWhitelist")
        pTag.getIntArray("modules").forEachIndexed { idx, module ->
            modules[idx] = ModuleType.entries[module]
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        saveToTag(pTag)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        loadFromTag(pTag)
    }

    override fun getUpdateTag(): CompoundTag {
        val tag = CompoundTag()
        saveToTag(tag)
        return tag
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener?>? {
        val tag = updateTag

        return ClientboundBlockEntityDataPacket.create(this) { tag }
    }

    override fun onDataPacket(net: Connection?, pkt: ClientboundBlockEntityDataPacket?) {
        val tag = pkt?.tag
            ?: return

        loadFromTag(tag)
    }

    //#endregion

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Network: ${network.id}" }
    }
}

/**
 * Flywheel rendering for the modules only. I think there are other ways to inject the needed data into the block's rendering, but meh.
 * */
class FluidPipeBlockEntityVisual(ctx: VisualizationContext, blockEntity: FluidPipeBlockEntity, partialTick: Float) : AbstractBlockEntityVisual<FluidPipeBlockEntity>(ctx, blockEntity, partialTick), SimpleDynamicVisual {
    data class InstanceData(var moduleType: FluidPipeBlockEntity.ModuleType, var instance: TransformedInstance?)

    private val instances = Array<InstanceData>(6) {
        InstanceData(FluidPipeBlockEntity.ModuleType.None, null)
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        for (i in 0 until 6) {
            val renderedModule = instances[i]
            val targetModule = blockEntity.modules[i]

            if(renderedModule.moduleType == targetModule) {
                continue
            }

            renderedModule.moduleType = targetModule
            renderedModule.instance?.delete()
            renderedModule.instance = if (targetModule.modelSupplier != null) {
                val result = visualizationContext.instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(targetModule.modelSupplier.get()))
                    .createInstance()
                    .also {
                        it.translate(visualPosition)
                        it.center()
                        it.rotateToFace(Direction.from3DDataValue(i))
                        it.uncenter()
                    }

                relight(result)
                result
            }
            else {
                null
            }
        }
    }

    override fun updateLight(p0: Float) {
        relight(instances.map { it.instance })
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        instances.forEach {
            p0.accept(it.instance)
        }
    }

    override fun _delete() {
        instances.forEach {
            it.instance?.delete()
        }
    }
}
