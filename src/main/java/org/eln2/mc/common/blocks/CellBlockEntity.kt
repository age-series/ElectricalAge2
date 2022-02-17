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
import org.eln2.mc.common.In
import org.eln2.mc.common.PlacementRotation
import org.eln2.mc.common.Side
import org.eln2.mc.common.cell.*
import java.util.*
import kotlin.system.measureNanoTime

class CellBlockEntity(var pos : BlockPos, var state: BlockState): BlockEntity(BlockRegistry.CELL_BLOCK_ENTITY.get(), pos, state) {
    private lateinit var manager : CellGraphManager
    lateinit var cell : AbstractCell

    private val serverLevel get() = level as ServerLevel
    private var adjacentUpdateRequired = true
    private var neighbourCache : ArrayList<CellBlockEntity>? = null
    private lateinit var connectPredicate : ((dir : Direction) -> Boolean)

    /**
     * Called by the block when one of our neighbours is updated.
     * We use this to invalidate the adjacency cache. Next time we use it, we will query the world for the changes.
     * @see adjacentUpdateRequired
    */
    @Suppress("UNUSED_PARAMETER") // Will very likely be needed later and helps to know the name of the args.
    @In(Side.LogicalServer)
    fun neighbourUpdated(pos : BlockPos) {
        adjacentUpdateRequired = true
    }

    /**
     * Called by the block when it is placed.
     * For now, we are only processing this for the server.
     * It will add our cell to an existing circuit, or join multiple existing circuits, or create a new one
     * with ourselves.
    */
    @Suppress("UNUSED_PARAMETER") // Will very likely be needed later and helps to know the name of the args.
    @In(Side.LogicalServer)
    fun setPlacedBy(
        level : Level,
        position : BlockPos,
        blockState : BlockState,
        entity : LivingEntity?,
        itemStack : ItemStack,
        cellProvider: CellProvider
    ) {
        if(level.isClientSide){
            return
        }

        cell = cellProvider.create(position)
        cell.tile = this
        cell.id = cellProvider.registryName!!

        val placeDir = PlacementRotation (state.getValue(HorizontalDirectionalBlock.FACING))

        connectPredicate = {
            connectionPredicate(placeDir, it, cellProvider)
        }

        manager = CellGraphManager.getFor(level as ServerLevel)

        registerIntoCircuit()
        cell.setPlaced()

        setChanged()

        cell.graph.build()
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
    @In(Side.LogicalServer)
    fun setDestroyed() {
        cell.destroy()

        val adjacentTiles = HashSet(getAdjacentCellTilesFast())

        if(adjacentTiles.isEmpty()){
           // we are alone
            cell.graph.destroyAndRemove()
            return
        }

        if(adjacentTiles.count() == 1){
            adjacentTiles.first().cell.connections.remove(cell)
            adjacentTiles.first().cell.update(connectionsChanged = true, graphChanged = false)
            return
        }

        fun isVisited(c : AbstractCell) : Boolean{
            return c.graph != cell.graph
        }

        val queue = LinkedList<AbstractCell>()
        val nanoTime = measureNanoTime {

            adjacentTiles.forEach{
                it.cell.connections.remove(cell)
            }

            adjacentTiles.forEach { neighbour ->
                if(isVisited(neighbour.cell)){
                    return@forEach
                }

                val results = CellGraph(UUID.randomUUID(), manager)

                queue.add(neighbour.cell)

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

                manager.addGraph(results)
                results.build()
            }
        }

        Eln2.LOGGER.info("Remove nanoseconds: $nanoTime")

        cell.graph.destroyAndRemove()
    }

    /**
     * Called when the entity was placed into the world.
     * we search all adjacent tile entities. If they have the same circuit, we will add our cell to their circuit.
     * Else, we will delete their circuits and copy them over to the new one, that also includes us (we join the circuits together)
     * If there are no adjacent, compatible cells, we create a new circuit with only ourselves
    */
    @In(Side.LogicalServer)
    fun registerIntoCircuit() {
        val adjacent = getAdjacentCellTilesFast()

        if (adjacent.isNotEmpty()) {
            // there are compatible adjacent cells.
            val firstRemote = adjacent[0]

            if(adjacent.count() == 1) {
                // we do not need to join multiple circuits. We can just add ourselves to that circuit.
                addUsTo(firstRemote)
                firstRemote.cell.connections.add(cell)
                firstRemote.cell.update(connectionsChanged = true, graphChanged = false)
                cell.connections = ArrayList(adjacent.map { it.cell })
                cell.update(connectionsChanged = true, graphChanged = true)
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
                    it.cell.connections.add(cell)
                    it.cell.update(connectionsChanged = true, graphChanged = false)
                }

                cell.connections = ArrayList(adjacent.map { it.cell })
                cell.update(connectionsChanged = true, graphChanged = true)
            }

            return
        }

        // we are not adjacent to any components.
        // we will build a circuit containing ourselves

        val graph = CellGraph(UUID.randomUUID(), manager)
        graph.addCell(cell)
        cell.graph = graph
        manager.addGraph(graph)
        cell.connections = ArrayList()
        cell.update(connectionsChanged = true, graphChanged = true)
        setChanged()
    }

    /**
     * Will set our cell's connections to the neighbours but exclude the provided cell.
     * @param exclude The cell to exclude.
    */
    // TODO: Do we want this still?
    @In(Side.LogicalServer)
    private fun setCellConnectionsToAdjacentButExclude(exclude : AbstractCell){
        val adjacent = getAdjacentCellsFast()
        adjacent.remove(exclude)
        cell.connections = adjacent
    }

    /**
     * Will check if the tiles provided all share the same circuit.
     * @param tiles The tiles to check.
     * @return True if the tiles are part of the same circuit. Otherwise, false.
    */
    @In(Side.LogicalServer)
    private fun areAllPartOfSameCircuit(tiles : List<CellBlockEntity>) : Boolean {
        if(tiles.count() == 1){
            return true
        }

        val first = tiles[0]
        var result = true

        tiles.drop(1).forEach {
            if(first.cell.graph.id != it.cell.graph.id){
                result = false
                return@forEach
            }
        }

        return result
    }

    /**
     * Called when we are not joining different graphs together. It will add us to adjacent entity's graph.
     * @param adjacent The adjacent entity whose circuit we will add our cell to.
    */
    @In(Side.LogicalServer)
    private fun addUsTo(adjacent: CellBlockEntity) {
        val remoteGraph = adjacent.cell.graph

        // add ourselves to it
        remoteGraph.addCell(cell)

        cell.graph = remoteGraph

        setChanged()
    }

    /**
     * Called when we are placed adjacently to 2 or more cells that are port of different graphs.
     * @param adjacent The adjacent tiles to us.
    */
    @In(Side.LogicalServer)
    private fun concatenateCircuitAndAddUs(adjacent : ArrayList<CellBlockEntity>){
        // create a new circuit that contains all cells

        val newCircuit = CellGraph(UUID.randomUUID(), manager)

        val visitedGraphs = HashSet<CellGraph>()

        val adjacentFast = HashSet(adjacent.map { it.cell })

        adjacent.forEach{
            it.cell.connections.add(cell)
        }

        adjacent.forEach {
            // join cells from all circuits
            val oldCircuit = it.cell.graph

            if(visitedGraphs.contains(oldCircuit)){
                // it shares a circuit with a tile we processed earlier
                return@forEach
            }

            visitedGraphs.add(oldCircuit)

            // copy all cells to new circuit
            oldCircuit.copyTo(newCircuit)
            oldCircuit.destroyAndRemove()
        }

        newCircuit.addCell(cell)

        // so that connectionsChanged becomes true
        adjacentFast.add(cell)
        cell.connections = getAdjacentCellsFast()
        cell.graph = newCircuit

        newCircuit.cells.forEach{ targetCell ->
            targetCell.graph = newCircuit
            targetCell.update(connectionsChanged = adjacentFast.contains(targetCell), graphChanged = true)
        }

        manager.addGraph(newCircuit)
    }

    /**
     * Will get the direction of adjacent cells to us. Warning! this does not use a caching mechanism, it will query the world.
     * @return The directions where adjacent cells are located.
    */
    // TODO: Do we want this still?
    @In(Side.LogicalServer)
    fun getAdjacentSides() : LinkedList<Direction>  {
        val result = LinkedList<Direction>()

        fun getAndAdd(dir : Direction){
            if(getAdjacentTile(dir) != null && connectPredicate(dir)) result.add(dir)
        }

        getAndAdd(Direction.NORTH)
        getAndAdd(Direction.SOUTH)
        getAndAdd(Direction.EAST)
        getAndAdd(Direction.WEST)

        return result
    }

    /**
     * Will prepare a list of adjacent cells using the connection predicate. This uses a caching mechanism
     * @see getAdjacentCellsFast
     * @see neighbourCache
     * in order to prevent querying the world when not necessary.
     * @return A new array, containing the adjacent cells.
    */
    @In(Side.LogicalServer)
    private fun getAdjacentCellsFast() : ArrayList<AbstractCell> {
        val adjacent = getAdjacentCellTilesFast()

        val result = ArrayList<AbstractCell>(adjacent.count())

        adjacent.forEach {
            result.add(it.cell)
        }

        return result
    }

    /**
     * Will return the adjacent tile cache or query the world, apply the connection predicate, update the cache, and return it.
     * @return The list of adjacent tiles.
     * @see neighbourCache
     * @see adjacentUpdateRequired
    */
    @In(Side.LogicalServer)
    private fun getAdjacentCellTilesFast() : ArrayList<CellBlockEntity>{
        if(!adjacentUpdateRequired && neighbourCache != null){
            return neighbourCache!!
        }

        adjacentUpdateRequired = false

        val nodes = ArrayList<CellBlockEntity>()

        fun getAndAdd(dir : Direction) {
            val node = getAdjacentTile(dir)
            if(node != null && node.canConnectFrom(this, dir)){
                nodes.add(node)
            }
        }

        getAndAdd(Direction.NORTH)
        getAndAdd(Direction.SOUTH)
        getAndAdd(Direction.EAST)
        getAndAdd(Direction.WEST)

        return nodes
    }

    /**
     * Queries the world for the tile present in the specified direction.
     * @param dir The direction to search in.
     * @return The tile if found, or null if there is no tile at that position.
    */
    @In(Side.LogicalServer)
    private fun getAdjacentTile(dir : Direction) : CellBlockEntity?{
        val level = getLevel()!!
        val remotePos = pos.relative(dir)
        val remoteEnt = level.getBlockEntity(remotePos)

        return remoteEnt as CellBlockEntity?
    }

    /**
     * Applies the connection predicate for that direction.
     * @param entity The entity we are checking.
     * @param dir The direction towards entity.
     * @return True if the connection is accepted.
    */
    @Suppress("UNUSED_PARAMETER") // Will very likely be needed later and helps to know the name of the args.
    @In(Side.LogicalServer)
    private fun canConnectFrom(entity : CellBlockEntity, dir : Direction) : Boolean {
        return connectPredicate(dir.opposite)

    }

    override fun saveAdditional(pTag: CompoundTag) {
        if(!cell.hasGraph()){
            Eln2.LOGGER.info("save additional: graph null")
            return
        }
        pTag.putString("GraphID", cell.graph.id.toString())
    }

    // remark: when the load method is called, the level is null.
    // we need the level in order to get the cell manager.
    // thus, we store the graph ID in this variable.
    // the level becomes available when setLevel is called,
    // so we load all our data there.
    private lateinit var loadGraphId : UUID

    // attention: the level is null here
    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if(!pTag.contains("GraphID")) {
            return
        }

        loadGraphId = UUID.fromString(pTag.getString("GraphID"))!!
        Eln2.LOGGER.info("tile load $pos")
        // warning! level is not available at this stage!
        // we override setLevel in order to load the rest of the data.
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()

        if(level!!.isClientSide) {
            return
        }

        cell.tileUnloaded()

        // GC reference tracking
        cell.tile = null
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(level!!.isClientSide){
            // we only load for the server.
            return
        }

        // level is now not null

        manager = CellGraphManager.getFor(serverLevel)

        if(!this::loadGraphId.isInitialized || !manager.containsGraphWithId(loadGraphId)) {
            return
        }

        // fetch graph with ID
        val graph = manager.getGraphWithId(loadGraphId)

        // fetch cell instance
        cell = graph.getCellAt(pos)
        cell.tile = this
        cell.tileLoaded()

        // fetch provider
        val provider = CellRegistry.registry.getValue(cell.id)!!

        val absolute = blockState.getValue(HorizontalDirectionalBlock.FACING)
        val placeDir = PlacementRotation(absolute)

        connectPredicate = {
            connectionPredicate(placeDir, absolute, provider)
        }
    }
}
