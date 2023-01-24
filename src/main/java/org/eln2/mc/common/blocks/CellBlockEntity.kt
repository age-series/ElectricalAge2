package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.eln2.mc.Eln2
import org.eln2.mc.common.PlacementRotation
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.*
import org.eln2.mc.extensions.BlockEntityExtensions.getNeighborEntity
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.measureNanoTime

class CellBlockEntity(var pos : BlockPos, var state: BlockState): BlockEntity(BlockRegistry.CELL_BLOCK_ENTITY.get(), pos, state) {
    lateinit var graphManager : CellGraphManager
    private lateinit var cellProvider : CellProvider

    // Cell is not available on the client.
    var cell : CellBase? = null

    private val serverLevel get() = level as ServerLevel

    fun getPlacementRotation() : PlacementRotation{
        return PlacementRotation (state.getValue(HorizontalDirectionalBlock.FACING))
    }

    fun getLocalDirection(globalDirection : Direction) : RelativeRotationDirection{
        val placementRotation = getPlacementRotation()

        return placementRotation.getRelativeFromAbsolute(globalDirection)
    }

    /**
     * Called by the block when it is placed.
     * For now, we are only processing this for the server.
     * It will add our cell to an existing circuit, or join multiple existing circuits, or create a new one
     * with ourselves.
    */
    @Suppress("UNUSED_PARAMETER") // Will very likely be needed later and helps to know the name of the args.
    fun setPlacedBy(
        level : Level,
        position : BlockPos,
        blockState : BlockState,
        entity : LivingEntity?,
        itemStack : ItemStack,
        cellProvider: CellProvider
    ) {
        // circuits are not built on the client

        if(level.isClientSide){
            return
        }

        cell = cellProvider.create(position)
        cell!!.tile = this
        cell!!.id = cellProvider.registryName!!

        val placeDir = PlacementRotation (state.getValue(HorizontalDirectionalBlock.FACING))

        Eln2.LOGGER.info("north <-> ${placeDir.getRelativeFromAbsolute(Direction.NORTH)}")
        Eln2.LOGGER.info("south <-> ${placeDir.getRelativeFromAbsolute(Direction.SOUTH)}")
        Eln2.LOGGER.info("east  <-> ${placeDir.getRelativeFromAbsolute(Direction.EAST)}")
        Eln2.LOGGER.info("west  <-> ${placeDir.getRelativeFromAbsolute(Direction.WEST)}")

        graphManager = CellGraphManager.getFor(level as ServerLevel)

        registerIntoCircuit()
        cell!!.setPlaced()

        setChanged()

        cell!!.graph.build()
    }

    private fun connectionPredicate(place : PlacementRotation, dir : Direction, provider: CellProvider) : Boolean{
        val relative = place.getRelativeFromAbsolute(dir)

        if(!provider.connectableDirections.contains(relative)){
            return false
        }

        return provider.connectionPredicate(relative)
    }

    /**
     * Called by the block when it is broken.
     * For now, we are only doing processing for the server.
    */
    fun setDestroyed() {
        if (cell == null)
            return
        cell!!.destroy()

        val adjacentTiles = HashSet(getAdjacentCellTilesFast())

        if(adjacentTiles.isEmpty()){
           // we are alone
            cell!!.graph.destroy()
            return
        }

        if(adjacentTiles.count() == 1){
            adjacentTiles.first().cell!!.connections.remove(cell)
            adjacentTiles.first().cell!!.update(connectionsChanged = true, graphChanged = false)
            return
        }

        fun isVisited(c : CellBase) : Boolean{
            return c.graph != cell!!.graph
        }

        val queue = LinkedList<CellBase>()
        val nanoTime = measureNanoTime {

            adjacentTiles.forEach{
                it.cell!!.connections.remove(cell)
            }

            adjacentTiles.forEach { neighbour ->
                if(isVisited(neighbour.cell!!)){
                    return@forEach
                }

                val results = CellGraph(UUID.randomUUID(), graphManager)

                queue.add(neighbour.cell!!)

                while(queue.isNotEmpty()){
                    val element = queue.remove()

                    if(isVisited(element)) {
                        continue
                    }

                    element.graph = results
                    element.update(connectionsChanged = adjacentTiles.contains(element.tile), graphChanged = true)
                    results.addCell(element)

                    element.connections.forEach{ child ->
                        queue.add(child)
                    }
                }

                graphManager.addGraph(results)
                results.build()
            }
        }

        Eln2.LOGGER.info("Remove nanoseconds: $nanoTime")

        cell!!.graph.destroy()
    }

    /**
     * Called when the entity was placed into the world.
     * we search all adjacent tile entities. If they have the same circuit, we will add our cell to their circuit.
     * Else, we will delete their circuits and copy them over to the new one, that also includes us (we join the circuits together)
     * If there are no adjacent, compatible cells, we create a new circuit with only ourselves
    */
    fun registerIntoCircuit() {
        val adjacent = getAdjacentCellTilesFast()

        if (adjacent.isNotEmpty()) {
            // there are compatible adjacent cells.
            val firstRemote = adjacent[0]

            if(adjacent.count() == 1) {
                // we do not need to join multiple circuits. We can just add ourselves to that circuit.
                addUsTo(firstRemote)
                firstRemote.cell!!.connections.add(cell!!)
                firstRemote.cell!!.update(connectionsChanged = true, graphChanged = false)
                cell!!.connections = ArrayList(adjacent.map { it.cell })
                cell!!.update(connectionsChanged = true, graphChanged = true)
                return
            }

            // perf: if adjacent tiles are from the same circuit, we do not actually need to create a new circuit.
            // we can just add ourselves to their circuit.
            val areAllIdentical = areAllPartOfSameCircuit(adjacent)

            if(!areAllIdentical){
                concatenateCircuitAndAddUs(adjacent)
            } else {
                // all adjacent tiles are of the same circuit. we can join their circuit.
                addUsTo(firstRemote)

                adjacent.forEach {
                    it.cell!!.connections.add(cell!!)
                    it.cell!!.update(connectionsChanged = true, graphChanged = false)
                }

                cell!!.connections = ArrayList(adjacent.map { it.cell })
                cell!!.update(connectionsChanged = true, graphChanged = true)
            }

            return
        }

        // we are not adjacent to any components.
        // we will build a circuit containing ourselves

        val graph = CellGraph(UUID.randomUUID(), graphManager)
        graph.addCell(cell!!)
        cell!!.graph = graph
        graphManager.addGraph(graph)
        cell!!.connections = ArrayList()
        cell!!.update(connectionsChanged = true, graphChanged = true)
        setChanged()
    }


    /**
     * Will check if the tiles provided all share the same circuit.
     * @param tiles The tiles to check.
     * @return True if the tiles are part of the same circuit. Otherwise, false.
    */
    private fun areAllPartOfSameCircuit(tiles : List<CellBlockEntity>) : Boolean {
        if (tiles.count() != 1) {
            val first = tiles[0]
            var result = true

            tiles.drop(1).forEach {
                if(first.cell!!.graph.id != it.cell!!.graph.id){
                    result = false
                    return@forEach
                }
            }

            return result
        }
        return true
    }

    /**
     * Called when we are not joining different graphs together. It will add us to adjacent entity's graph.
     * @param adjacent The adjacent entity whose circuit we will add our cell to.
    */
    private fun addUsTo(adjacent: CellBlockEntity) {
        val remoteGraph = adjacent.cell!!.graph

        // add ourselves to it
        remoteGraph.addCell(cell!!)

        cell!!.graph = remoteGraph

        setChanged()
    }

    /**
     * Called when we are placed adjacently to 2 or more cells that are port of different graphs.
     * @param adjacent The adjacent tiles to us.
    */
    private fun concatenateCircuitAndAddUs(adjacent : ArrayList<CellBlockEntity>){
        // create a new circuit that contains all cells

        val newCircuit = CellGraph(UUID.randomUUID(), graphManager)

        val visitedGraphs = HashSet<CellGraph>()

        val adjacentFast = HashSet(adjacent.map { it.cell })

        adjacent.forEach{
            it.cell!!.connections.add(cell!!)
        }

        adjacent.forEach {
            // join cells from all circuits
            val oldCircuit = it.cell!!.graph

            if(visitedGraphs.contains(oldCircuit)){
                // it shares a circuit with a tile we processed earlier
                return@forEach
            }

            visitedGraphs.add(oldCircuit)

            // copy all cells to new circuit
            oldCircuit.copyTo(newCircuit)
            oldCircuit.destroy()
        }

        newCircuit.addCell(cell!!)

        // so that connectionsChanged becomes true
        adjacentFast.add(cell)
        cell!!.connections = getAdjacentCellsFast()
        cell!!.graph = newCircuit

        newCircuit.cells.forEach{ targetCell ->
            targetCell.graph = newCircuit
            targetCell.update(connectionsChanged = adjacentFast.contains(targetCell), graphChanged = true)
        }

        graphManager.addGraph(newCircuit)
    }

    private fun getCandidateNeighborEntities() : ArrayList<CellBlockEntity>{
        val results = ArrayList<CellBlockEntity>()

        Direction.values().forEach { direction ->
            val entity = this.getNeighborEntity<CellBlockEntity>(direction)

            if(entity != null && entity.canConnectFrom(direction.opposite)){
                results.add(entity)
            }
        }

        return results
    }

    fun getNeighborCells() : ArrayList<CellBase>{
        val results = ArrayList<CellBase>();

        getCandidateNeighborEntities().forEach { entity ->
            if(entity.cell == null){
                // The method may be called when the entity is being placed, and the circuits are being built.
                // The cell will be null. We ignore it here.

                return@forEach
            }

            results.add(entity.cell!!)
        }

        return results;
    }

    private fun canConnectFrom(dir : Direction) : Boolean {
        val localDirection = getLocalDirection(dir)

        return cellProvider.canConnectFrom(localDirection)
    }

    override fun saveAdditional(pTag: CompoundTag) {
        if (cell!!.hasGraph()) {
            pTag.putString("GraphID", cell!!.graph.id.toString())
        } else {
            Eln2.LOGGER.info("Save additional: graph null")
        }
    }

    private lateinit var savedGraphID : UUID

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if (pTag.contains("GraphID")) {
            savedGraphID = UUID.fromString(pTag.getString("GraphID"))!!
            Eln2.LOGGER.info("Deserialized cell entity at $pos")
        }
        else{
            Eln2.LOGGER.warn("Cell entity at $pos does not have serialized data.")
        }
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()

        if (!level!!.isClientSide) {
            cell!!.tileUnloaded()

            // GC reference tracking
            cell!!.tile = null
        }
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (level!!.isClientSide) {
            return
        }

        // here, we can get our manager. We have the level at this point.

        graphManager = CellGraphManager.getFor(serverLevel)

        if (this::savedGraphID.isInitialized && graphManager.containsGraphWithId(savedGraphID)) {
            // fetch graph with ID
            val graph = graphManager.getGraphWithId(savedGraphID)

            // fetch cell instance
            println("Loading cell at location $pos")

            cell = graph.getCellAt(pos)

            cellProvider = CellRegistry.getProvider(cell!!.id)

            cell!!.tile = this
            cell!!.tileLoaded()
        }
    }

    fun getHudMap(): Map<String, String> {
        return if (cell == null) {
            Eln2.LOGGER.warn("You're trying to reference cell in getHudMap from the client side, where cell is always null!")
            mapOf()
        } else {
            cell!!.getHudMap()
        }
    }
}
