package org.eln2.mc.common.cell

import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.container.CellNeighborInfo
import org.eln2.mc.common.cell.container.CellSpaceLocation
import org.eln2.mc.common.cell.container.ICellContainer

object CellConnectionManager {
    fun connect(container : ICellContainer, cellSpace : CellSpaceLocation){
        val cellSpaces = container.getCells()

        registerCell(cellSpace, container)

        // Notify the cell of the new placement.
        cellSpace.cell.onPlaced()

        // Finally, build all the solvers
        cellSpaces.map { it.cell.graph }.distinct().forEach { graph ->
            graph.build()
        }
    }

    fun destroy(cellSpace: CellSpaceLocation, container: ICellContainer){
        removeCell(cellSpace, container)
        cellSpace.cell.onDestroyed()
    }

    private fun removeCell(cellSpace : CellSpaceLocation, container: ICellContainer){
        val cell = cellSpace.cell
        val manager = container.manager
        val neighborCells = container.queryNeighbors(cellSpace)

        val graph = cell.graph

        // This is common logic for all cases.
        neighborCells.forEach { neighborInfo ->
            neighborInfo.neighborSpace.cell.connections.remove(cell)
            neighborInfo.neighborContainer.recordDeletedConnection(neighborInfo.neighborSpace, neighborInfo.neighborDirection)
        }

        /* Cases:
        *   1. We don't have any neighbors. We can destroy the circuit.
        *   2. We have a single neighbor. We can remove ourselves from the circuit.
        *   3. We have multiple neighbors, and we are not a cut vertex. We can remove ourselves from the circuit.
        *   4. We have multiple neighbors, and we are a cut vertex. We need to remove ourselves, find the new disjoint graphs,
        *        and rebuild the circuits.
        */

        if(neighborCells.isEmpty()){
            // Case 1. Destroy this circuit.

            // Make sure we don't make any logic errors somewhere else.
            assert(graph.cells.size == 1)

            graph.destroy()
        }
        else if(neighborCells.size == 1){
            // Case 2.

            // Remove the cell from the circuit.
            graph.removeCell(cell)

            val neighbor = neighborCells[0].neighborSpace

            neighbor.cell.update(connectionsChanged = true, graphChanged = false)

            // todo: do we need to rebuild the solver?

            graph.build()
        }
        else{
            // Case 3 and 4. Implement a more sophisticated algorithm, if necessary.

            graph.destroy()
            rebuildTopologies(neighborCells, cell, manager)
        }
    }

    private fun registerCell(cellSpace : CellSpaceLocation, container: ICellContainer){
        val manager = container.manager
        val cell = cellSpace.cell
        val neighborInfos = container.queryNeighbors(cellSpace)
        val neighborCells = neighborInfos.map { it.neighborSpace.cell }.toHashSet()

        /*
        * Cases:
        *   1. We don't have any neighbors. We must create a new circuit.
        *   2. We have a single neighbor. We can add this cell to their circuit.
        *   3. We have multiple neighbors, but they are part of the same circuit. We can add this cell to the common circuit.
        *   4. We have multiple neighbors, and they are part of different circuits. We need to create a new circuit,
        *       that contains the cells of the other circuits, plus this one.
        * */

        // This is common logic for all cases

        cell.connections = ArrayList(neighborInfos.map { it.neighborSpace.cell })

        neighborInfos.forEach { neighborInfo ->
            Eln2.LOGGER.info("Neighbor $neighborInfo")

            neighborInfo.neighborSpace.cell.connections.add(cell)
            neighborInfo.neighborContainer.recordConnection(neighborInfo.neighborSpace, neighborInfo.neighborDirection, cellSpace)

            container.recordConnection(cellSpace, neighborInfo.sourceDirection, neighborInfo.neighborSpace)
        }

        if(neighborInfos.isEmpty()){
            // Case 1. Create new circuit

            val graph = manager.createGraph()
            graph.addCell(cell)
        }
        else if(haveCommonCircuit(neighborInfos)){
            // Case 2 and 3. Join the existing circuit.

            val graph = neighborInfos[0].neighborSpace.cell.graph

            graph.addCell(cell)

            // Add this cell to the neighbors' connections.

            neighborInfos.forEach { neighborInfo ->
                neighborInfo.neighborSpace.cell.update(connectionsChanged = true, graphChanged = false)
            }

            // todo: do we need to rebuild the solver?
            graph.build()
        }
        else{
            // Case 4. We need to create a new circuit, with all cells and this one.

            // Identify separate circuits.
            val disjointGraphs = neighborInfos.map { it.neighborSpace.cell.graph }.distinct()

            // Create new circuit.
            val graph = manager.createGraph()

            // Register current cell

            graph.addCell(cell)

            // Copy cells over to the new circuit and destroy previous circuits.
            disjointGraphs.forEach{ existingGraph ->
                existingGraph.copyTo(graph)

                // We also need to refit the existing cells

                existingGraph.cells.forEach { cell ->
                    cell.graph = graph

                    // We need this for the update method.
                    // We set connectionsChanged to true only if this cell is also
                    // a neighbor of the cell we are inserting.
                    val isNeighbor = neighborCells.contains(cell)

                    cell.update(connectionsChanged = isNeighbor, graphChanged = true)
                    cell.container?.topologyChanged()
                }

                existingGraph.destroy()
            }
        }

        // This cell had a complete update.
        cell.update(connectionsChanged = true, graphChanged = true)
        cell.container?.topologyChanged()
    }

    private fun haveCommonCircuit(neighbors : ArrayList<CellNeighborInfo>) : Boolean{
        if(neighbors.size < 2){
            return true
        }

        val graph = neighbors[0].neighborSpace.cell.graph

        neighbors.drop(1).forEach {info ->
            if(info.neighborSpace.cell.graph != graph){
                return false
            }
        }

        return true
    }

    private fun rebuildTopologies(neighborInfos: ArrayList<CellNeighborInfo>, removedCell : CellBase, manager: CellGraphManager){
        /*
        * For now, we use this simple algorithm.:
        *   We enqueue all neighbors for visitation. We perform searches through their graphs,
        *   excluding the cell we are removing.
        *
        *   If at any point we encounter an unprocessed neighbor, we remove that neighbor from the neighbor
        *   queue.
        *
        *   After a queue element has been processed, we build a new circuit with the cells we found.
        * */

        val neighbors = neighborInfos.map { it.neighborSpace.cell }.toHashSet()
        val neighborQueue = ArrayDeque<CellBase>()
        neighborQueue.addAll(neighbors)

        val bfsVisited = HashSet<CellBase>()
        val bfsQueue = ArrayDeque<CellBase>()

        while (neighborQueue.size > 0){
            val neighbor = neighborQueue.removeFirst()

            // Create new circuit for all cells connected to this one.
            val graph = manager.createGraph()

            // Start BFS at the neighbor.
            bfsQueue.add(neighbor)

            while (bfsQueue.size > 0){
                val cell = bfsQueue.removeFirst()

                if(!bfsVisited.add(cell)){
                    continue
                }

                // Remove it from the neighbor queue, if it exists.
                // todo: can we add an exit condition here?
                // Hypothesis: If at any point, the neighbor queue becomes empty, we can stop traversal, and use the cells
                // in the old circuit, minus the one we are removing. This helps performance if there are close
                // cycles around the cell we are removing.
                neighborQueue.remove(cell)

                graph.addCell(cell)

                // Enqueue neighbors (excluding the cell we are removing) for processing
                cell.connections.forEach { connectedCell ->
                    // This must be handled above.
                    assert(connectedCell != removedCell)

                    bfsQueue.add(connectedCell)
                }
            }

            assert(bfsQueue.isEmpty())

            // Refit cells
            graph.cells.forEach { cell ->
                val isNeighbor = neighbors.contains(cell)

                cell.update(connectionsChanged = isNeighbor, graphChanged = true)
                cell.container?.topologyChanged()
            }

            // Finally, build the solver.

            graph.build()

            // We don't need to keep the cells, we have already traversed all the connected ones.
            bfsVisited.clear()
        }
    }
}
