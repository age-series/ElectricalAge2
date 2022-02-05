package org.eln2.mc.common.blocks

import com.google.gson.Gson
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.logging.log4j.LogManager
import org.eln2.mc.common.*
import org.eln2.mc.common.cell.*
import org.eln2.mc.extensions.ServerLevelExtensions.getCellEntityAt
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.concurrent.thread

class CellTileEntity(var pos : BlockPos, var state: BlockState): BlockEntity(BlockRegistry.TEST_CELL_ENTITY.get(), pos, state) {
    private val _logger = LogManager.getLogger()
    // null before setPlacedBy or when being searched after the graph was split
    private var _graph : CellGraph? = null
    // null before setPlacedBy
    private var _manager : CellGraphManager? = null
    // null before setPlacedBy
    private var _cell : CellBase? = null

    private val manager get() = _manager!!
    val graph get() = _graph!!
    val cell get() = _cell!!
    val serverLevel get() = level as ServerLevel

    // perf: caching the neighbour blocks to avoid
    // calling world get methods.
    // this becomes false when the adjacent blocks are read,
    // and true when the neighbour updated event is called.
    private var _adjacentCellUpdateRequired = true
    private var _neighbourCache : ArrayList<CellTileEntity>? = null

    // used to check if a connection can be made for a certain direction.
    // null before setPlacedBy
    private var _connectPredicate : ((dir : Direction) -> Boolean)? = null
    // used to fetch the cell factory from registry
    // null before setPlacedBy
    private var _cellId : ResourceLocation? = null

    val connectionPredicate get() = _connectPredicate!!
    val cellId get() = _cellId!!

    // this returns the adjacent tiles or fetches them then caches them.
    // this does not affect performance if neighbours are not updated!
    // see _adjacentCellUpdateRequired above
    val adjacent get() = getAdjacentCellTilesFast()

    /**
     * Called by the block when one of our neighbours is updated.
     * We use this to invalidate the adjacency cache. Next time we use it, we will query the world for the changes.
     * @see _adjacentCellUpdateRequired
    */
    @In(Side.LogicalServer)
    fun neighbourUpdated(pos : BlockPos) {
        _adjacentCellUpdateRequired = true
    }

    /**
     * Called by the block when it is placed.
     * For now, we are only processing this for the server.
     * It will add our cell to an existing circuit, or join multiple existing circuits, or create a new one
     * with ourselves.
    */
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

        _cellId = cellProvider.registryName

        // map the cell ID to a factory
        _cell = cellProvider.create(pos)
        cell.setId(cellId)

        _connectPredicate = {
            cellProvider.connectionPredicate(it)
        }

        _manager = CellGraphManager.getFor(level as ServerLevel)

        registerIntoCircuit()

        // call place handler
        cell.setPlaced(getAdjacentCellsFast())

        setChanged()
    }

    /**
     * Called by the block when it is broken.
     * For now, we are only doing processing for the server.
    */
    @In(Side.LogicalServer)
    fun setDestroyed() {
        cell.destroy()

        val adjacent = getAdjacentCellTilesFast()
        _logger.info("${this.pos} is performing a remove!")

        if(adjacent.count() < 2){
            // we cannot split a circuit because we are adjacent to only one component.

            removeToOneCircuit()

            if(adjacent.isEmpty()) {
                // we are actually alone, we can destroy the graph.
                graph.destroy()
                _logger.info("Also destroyed our graph.")
            } else {
                removeOurselvesFromTheirCell(adjacent[0])
                adjacent[0].updateCellConnectionsButExclude(cell)
            }

            return
        }

        // it is possible that our removal will split the graph into multiple pieces.
        // 1. we will invalidate their graph (set it to null)
        // 2. we will then perform a search for each of our adjacent tiles that excludes us and sets
        // the graph of the adjacent cells to the old one. 3. When we move to the next neighbour, if their
        // graph is not null, it means they were adjacent to a neighbour we already searched. We can leave them alone.
        // 4. if it is null, then we have identified a fragment of the graph. We will create a new graph for it then move on.

        // remove ourselves so we don't get invalidated
        graph.removeCell(cell)

        // 1. invalidate all graphs
        graph.cells.forEach{
            serverLevel.getCellEntityAt(it.pos).setGraphInvalid(this)
        }

        // for plotting
        val plotList = LinkedList<CellGraph>()

        // clear graph for new search
        graph.clear()

        adjacent.forEachIndexed{index, remote ->
            if(index == 0) {
                _logger.info("reusing graph!")
                // we can reuse the current graph for this one.

                // 2. perform the search
                remote.searchAndBuildAdjacentList(graph, this, this, true)
                _logger.info("built list!")
                // the graph is ready. queue for plotting!
                plotList.add(graph)
                _logger.info("reused, have: ${graph.cells.count()}, us: ${graph.cells.contains(cell)}")

                _graph = null

                return@forEachIndexed
            }

            // 3. if they have a graph, they have been searched before.
            // thus, they are part of an earlier graph
            if(remote.hasCircuit()){
                _logger.info("Detected pre-searched! they have ${remote.graph.cells.map { it.pos }}")

                // their cell still needs updating
                remote.updateCellConnectionsAndGraphButExclude(cell)
                return@forEachIndexed
            }

            // they are disconnected from our previous neighbours.
            // means we were the only connection point between them.

            // 4. create new graph

            val new = CellGraph(UUID.randomUUID(), manager)

            // 2. perform the search

            remote.searchAndBuildAdjacentList(new, this, this, false)

            // graph is ready, queue for plotting
            plotList.add(new)

            _logger.info("created new. Have ${new.cells.count()}")

            setChanged()
        }

        setChanged()

        thread (start = true) {
            plotList.forEach {
                connectAndSend(it)
                Thread.sleep(2000)
            }
        }
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
                addOurselvesToTheirGraph(firstRemote)
                firstRemote.setCellConnections(firstRemote.getAdjacentCellsFast())
                return
            }

            // perf: if adjacent tiles are from the same circuit, we do not actually need to create a new circuit.
            // we can just add ourselves to their circuit.
            val areAllIdentical = areAllPartOfSameCircuit(adjacent)

            if(!areAllIdentical){
                // we are joining 2 or more circuits

                adjacent.forEach{
                    _logger.info("N.A ID ${it.graph.id}")
                }

                concatenateCircuitAndAddOurselves(adjacent)
            } else {
                _logger.info("Detected single circuit across multiple sides. A new circuit is not required.")
                // all adjacent tiles are of the same circuit. we can join their circuit.
                addOurselvesToTheirGraph(firstRemote)

                adjacent.forEach {
                    it.setCellConnections(it.getAdjacentCellsFast())
                }
            }

            return
        }

        _logger.info("Creating new circuit with us!")

        // we are not adjacent to any components.
        // we will build a circuit containing ourselves

        val graph = CellGraph(UUID.randomUUID(), manager)
        graph.addCell(cell)
        _graph = graph
        manager.addGraph(graph)

        setChanged()
    }

    /**
     * Will set our cell's connections.
     * @param list The connections to set.
    */
    @In(Side.LogicalServer)
    private fun setCellConnections(list : ArrayList<CellBase>) {
        cell.setConnections(list)
    }

    /**
     * Will check if the tiles provided all share the same circuit.
     * @param tiles The tiles to check.
     * @return True if the tiles are part of the same circuit. Otherwise, false.
    */
    @In(Side.LogicalServer)
    private fun areAllPartOfSameCircuit(tiles : List<CellTileEntity>) : Boolean {
        if(tiles.count() == 1){
            return true
        }

        val first = tiles[0]
        var result = true

        tiles.drop(1).forEach {
            if(first.graph.id != it.graph.id){
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
    private fun addOurselvesToTheirGraph(adjacent: CellTileEntity) {
        val remoteGraph = adjacent.graph

        // add ourselves to it
        remoteGraph.addCell(cell)
        cell.setGraphAndConnections(remoteGraph, getAdjacentCellsFast())

        _graph = remoteGraph
        _logger.info("Added ourselves to ${remoteGraph.id}, with ${remoteGraph.cells.count()} cells")

        // plot
        connectAndSend(graph)

        setChanged()
    }

    /**
     * Called when we are placed adjacently to 2 or more cells that are port of different graphs.
     * @param adjacent The adjacent tiles to us.
    */
    @In(Side.LogicalServer)
    private fun concatenateCircuitAndAddOurselves(adjacent : ArrayList<CellTileEntity>){
        // create a new circuit that contains all cells

        val newCircuit = CellGraph(UUID.randomUUID(), manager)

        val alreadyProcessed = HashSet<CellGraph>()

        adjacent.forEach {
            // join cells from all circuits

            if(!it.hasCircuit()){
                // broken state because they should be in a graph, even if they are the only one in there
                _logger.error("Broken state!")
                throw Exception("detected broken node state! ${it.pos}")
            }

            val oldCircuit = it.graph

            if(alreadyProcessed.contains(oldCircuit)){
                // it shares a circuit with a tile we processed earlier
                return@forEach
            }

            alreadyProcessed.add(oldCircuit)

            // copy all cells to new circuit
            oldCircuit.copyTo(newCircuit)
            oldCircuit.destroy()
        }

        newCircuit.addCell(cell)

        newCircuit.cells.forEach{ targetCell ->
            // also sets it as our new circuit because we are on the list
            val tile = serverLevel.getCellEntityAt(targetCell.pos)
            // only sets the tile's graph, does not update the cell's graph
            tile.setTileGraph(newCircuit, this)

            // this, on the other hand, will update the cell's graph.
            tile.updateCellConnectionsAndGraph()
        }

        manager.addGraph(newCircuit)

        //plot
        connectAndSend(graph)
    }

    /**
     * Called by a broken tile, to update our cell's connections excluding the caller (broken tile).
     * @param adjacent The broken tile to exclude.
    */
    @In(Side.LogicalServer)
    private fun removeOurselvesFromTheirCell(adjacent : CellTileEntity){
        val oldConnections = adjacent.getAdjacentCellsFast()
        oldConnections.remove(cell)
        adjacent.setCellConnections(oldConnections)
    }

    /**
     * This method removes our cell from our graph.
    */
    @In(Side.LogicalServer)
    private fun removeToOneCircuit() {
        graph.removeCell(cell)
        _logger.info("Removed ourselves.")

        // plot
        connectAndSend(graph)
    }

    /**
     *  Searches all connected tiles and builds the circuit with them. This also adds us to the graph.
     *  @param caller The tile that executes this method. It will be excluded from our search.
     *  @param originator The tile that initiated the search. It will be excluded from the search.
     *  @param reuseGraph If false and not adjacent to the originator, we will only update our cell's graph, but not connections.
     *      If it is true, it means that the cell's graph is actually valid since we are reusing it.
    */
    @In(Side.LogicalServer)
    private fun searchAndBuildAdjacentList(graph : CellGraph, caller : CellTileEntity, originator : CellTileEntity, reuseGraph : Boolean){
        val adjacentToUs = getAdjacentCellTilesFast()
        adjacentToUs.remove(caller)

        if(adjacentToUs.contains(originator)){
            adjacentToUs.remove(originator)
        }

        setTileGraph(graph, caller)

        if(originator == caller){
            // we are the first level adjacent cell.
            // we need to update our graph AND connections!
            updateCellConnectionsAndGraphButExclude(originator.cell)
        } else if(!reuseGraph) {
            // we didn't actually nullify the cell's graph,
            // we nullified the tile's graph for searching.
            // thus, it is still valid

            // here, we are not reusing the graph
            // our adjacent cells also did not change,
            // since we are not next to the removed tile.
            updateGraph()
        }

        graph.addCell(this.cell)

        adjacentToUs.forEach{
            // this is an edge case. basically, we are not adjacent to them, but
            // they are still part of our graph
            if(!it.hasCircuit()){
                it.searchAndBuildAdjacentList(graph, this, originator, reuseGraph)
            }
        }
    }

    /**
     * This method will set the graph and connections (adjacent cells) for our cell.
    */
    @In(Side.LogicalServer)
    private fun updateCellConnectionsAndGraph() {
        val connections = getAdjacentCellsFast()
        cell.setGraphAndConnections(graph, connections)
    }

    /**
     * This method will set the connections of our cell using the adjacent cells, but will exclude the provided cell.
     * @param remoteCell The cell to be excluded from our adjacency list.
    */
    @In(Side.LogicalServer)
    private fun updateCellConnectionsButExclude(remoteCell : CellBase) {
        val connections = getAdjacentCellsFast()
        connections.remove(remoteCell)
        cell.setConnections(connections)
    }

    /**
     * This method will update the graph of our cell to the graph of this tile entity.
    */
    @In(Side.LogicalServer)
    private fun updateGraph(){
        cell.setGraph(graph)
    }

    /**
     * This method will set the graph and connections of our cell, but exclude the provided cell from the connections.
     * The graph is the one in our graph property.
     * @param remoteCell The cell to exclude from our adjacency list.
    */
    @In(Side.LogicalServer)
    private fun updateCellConnectionsAndGraphButExclude(remoteCell : CellBase) {
        val connections = getAdjacentCellsFast()

        if(connections.contains(remoteCell)){
            connections.remove(remoteCell)
        }

        cell.setGraphAndConnections(graph, connections)
        setChanged()
    }

    private fun connectAndSend(circuit : CellGraph) {
        val str = Gson().toJson(JsonFrame(circuit.cells.map {
            JsonCell(it.pos.x, it.pos.z, emptyList())
        }))
        val t = thread(start = true){
            val serverURL = "http://127.0.0.1:3141/"
            val post = HttpPost(serverURL)
            post.entity = StringEntity(str)
            val client = HttpClients.createDefault()
            val response = client.execute(post)
            response.close()
            client.close()
        }
    }

    private class JsonCell(val x : Int, val y : Int, val connections : List<Int>)
    private class JsonFrame(val cells : List<JsonCell>)

    private fun prepareJsonNode(tile : CellTileEntity) : JsonCell {
        val sides = ArrayList<Int>()

        tile.getAdjacentSides().forEach {
            when(it){
                Direction.NORTH -> sides.add(0)
                Direction.SOUTH -> sides.add(1)
                Direction.EAST -> sides.add(2)
                Direction.WEST -> sides.add(3)
            }
        }

        return JsonCell(tile.pos.x, tile.pos.z, sides)
    }

    /**
     * Sets the tile entity's graph. Does not update the graph of the cell!
     * @param graph The new graph.
     * @param caller The tile that called this method.
    */
    @In(Side.LogicalServer)
    private fun setTileGraph(graph : CellGraph, caller : CellTileEntity){
        _graph = graph
        setChanged()
    }

    /**
     * Called when the recursive search after a tile has been broken is being performed.
     * Will be used by the hasCircuit method to check if we were already searched (and thus adjacent to a previously searched cell)
     * @see hasCircuit
    */
    @In(Side.LogicalServer)
    private fun setGraphInvalid(caller : CellTileEntity){
        _graph = null
    }

    /**
     * Called when the recursive search after a tile has been broken is being performed.
     * It will check if we were adjacent to another tile, searched before us, thanks to the setGraphInvalid method.
     * @see setGraphInvalid
     * @return True if we were searched before.
    */
    @In(Side.LogicalServer)
    private fun hasCircuit() : Boolean {
        return _graph != null
    }

    /**
     * Will get the direction of adjacent cells to us. Warning! this does not use a caching mechanism, it will query the world.
     * @return The directions where adjacent cells are located.
    */
    @In(Side.LogicalServer)
    private fun getAdjacentSides() : LinkedList<Direction>  {
        val result = LinkedList<Direction>()

        fun getAndAdd(dir : Direction){
            if(getAdjacentTile(dir) != null && connectionPredicate(dir)) result.add(dir)
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
     * @see _neighbourCache
     * in order to prevent querying the world when not necessary.
     * @return A new array, containing the adjacent cells.
    */
    @In(Side.LogicalServer)
    private fun getAdjacentCellsFast() : ArrayList<CellBase> {
        val result = ArrayList<CellBase>(adjacent.count())

        adjacent.forEach {
            result.add(it.cell)
        }

        return result
    }

    /**
     * Will return the adjacent tile cache or query the world, apply the connection predicate, update the cache, and return it.
     * @return The list of adjacent tiles.
     * @see _neighbourCache
     * @see _adjacentCellUpdateRequired
    */
    @In(Side.LogicalServer)
    private fun getAdjacentCellTilesFast() : ArrayList<CellTileEntity>{
        if(!_adjacentCellUpdateRequired && _neighbourCache != null){
            return _neighbourCache!!
        }

        _adjacentCellUpdateRequired = false

        val nodes = ArrayList<CellTileEntity>()

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
    private fun getAdjacentTile(dir : Direction) : CellTileEntity?{
        val level = getLevel()!!
        val remotePos = pos.relative(dir)
        val remoteEnt = level.getBlockEntity(remotePos)

        return remoteEnt as CellTileEntity?
    }

    /**
     * Applies the connection predicate for that direction.
     * @param entity The entity we are checking.
     * @param dir The direction towards entity.
     * @return True if the connection is accepted.
    */
    @In(Side.LogicalServer)
    private fun canConnectFrom(entity : CellTileEntity, dir : Direction) : Boolean {
        val localDir = dir.opposite

        if(connectionPredicate(localDir)){
            return true
        }

        _logger.info("Rejected connection via direction: $localDir")

        return false
    }

    override fun saveAdditional(pTag: CompoundTag) {
        if(_graph == null){
            _logger.info("Cannot save additional data, we are not part of a graph!")
            return
        }

        pTag.putString("GraphID", graph.id.toString())
        _logger.info("Saved graph id to additional data!")
    }

    // remark: when the load method is called, the level is null.
    // we need the level in order to get the cell manager.
    // thus, we store the graph ID in this variable.
    // the level becomes available when setLevel is called,
    // so we load all our data there.
    private var _loadGraphId : UUID? = null

    // attention: the level is null here
    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if(!pTag.contains("GraphID")) {
            _logger.info("No graph found in extra data!")
            return
        }

        _logger.info("LEVEL IS CLIENT: ${level?.isClientSide}")

        _loadGraphId = UUID.fromString(pTag.getString("GraphID"))!!

        _logger.info("Read tile circuit ID from disk!")

        // warning! level is not available at this stage!
        // we override setLevel in order to load the rest of the data.
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(level!!.isClientSide){
            // we only load for the server.
            return
        }

        // level is now not null

        _manager = CellGraphManager.getFor(serverLevel)

        if(_loadGraphId == null || !manager.containsGraphWithId(_loadGraphId!!)) {
            _logger.info("No graph can be loaded.")
            return
        }

        // fetch graph with ID
        _graph = manager.getGraphWithId(_loadGraphId!!)

        // fetch cell instance
        _cell = graph.getCellAt(pos)

        // set ID
        _cellId = cell.id

        // fetch provider
        val provider = CellRegistry.registry.getValue(cellId)!!

        // set predicate

        _connectPredicate = {
            provider.connectionPredicate(it)
        }

        // all done!

        _logger.info("Completed tile cell loading from disk!")
    }
}
