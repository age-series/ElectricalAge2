package org.eln2.mc.common.cells.foundation

import org.eln2.mc.Eln2

/**
 * The Cell Connection Manager has all Cell-Cell connection logic, I.E. is responsible for building *physical* networks.
 * There are two key algorithms here:
 * - Cell Insertion
 *      - Inserts a cell into the world, and may form connections with other cells.
 *
 * - Cell Deletion
 *      - Deletes a cell from the world, and may result in many topological changes to the associated graph.
 *        An example would be the removal (deletion) of a cut vertex. This would result in the graph splintering into multiple disjoint graphs.
 *        This is the most intensive part of the algorithm. It may be optimized (the algorithm implemented here is certainly suboptimal),
 *        but it has been determined that this is not a cause for concern,
 *        as it only represents a small slice of the performance impact caused by network updates.
 *
 *
 * @see <a href="https://en.wikipedia.org/wiki/Biconnected_component">Wikipedia - Bi-connected component</a>
 * */
object CellConnectionManager {
    /**
     * Inserts a cell into a graph. It may create connections with other cells, and cause
     * topological changes to related networks.
     * */
    fun connect(container: ICellContainer, cellSpace: CellInfo) {
        registerCell(cellSpace, container)
        cellSpace.cell.onPlaced()
    }

    /**
     * Removes a cell from the graph. It may cause topological changes to the graph, as outlined in the top document.
     * */
    fun destroy(cellSpace: CellInfo, container: ICellContainer) {
        removeCell(cellSpace, container)
        cellSpace.cell.onDestroyed()
    }

    private fun removeCell(cellSpace: CellInfo, container: ICellContainer) {
        val cell = cellSpace.cell
        val manager = container.manager
        val neighborCells = container.queryNeighbors(cellSpace)

        val graph = cell.graph

        // Stop Simulation
        graph.stopSimulation()

        // This is common logic for all cases.
        neighborCells.forEach { neighborInfo ->
            neighborInfo.neighborInfo.cell.removeConnection(cell)
            neighborInfo.neighborContainer.recordDeletedConnection(
                neighborInfo.neighborInfo,
                neighborInfo.neighborDirection
            )
        }

        /* Cases:
        *   1. We don't have any neighbors. We can destroy the circuit.
        *   2. We have a single neighbor. We can remove ourselves from the circuit.
        *   3. We have multiple neighbors, and we are not a cut vertex. We can remove ourselves from the circuit.
        *   4. We have multiple neighbors, and we are a cut vertex. We need to remove ourselves, find the new disjoint graphs,
        *        and rebuild the circuits.
        */

        if (neighborCells.isEmpty()) {
            // Case 1. Destroy this circuit.

            // Make sure we don't make any logic errors somewhere else.
            assert(graph.cells.size == 1)

            graph.destroy()
        } else if (neighborCells.size == 1) {
            // Case 2.

            // Remove the cell from the circuit.
            graph.removeCell(cell)

            val neighbor = neighborCells[0].neighborInfo

            neighbor.cell.update(connectionsChanged = true, graphChanged = false)

            // todo: do we need to rebuild the solver?

            graph.buildSolver()
            graph.startSimulation()
        } else {
            // Case 3 and 4. Implement a more sophisticated algorithm, if necessary.

            graph.destroy()
            rebuildTopologies(neighborCells, cell, manager)
        }
    }

    private fun registerCell(cellSpace: CellInfo, container: ICellContainer) {
        val manager = container.manager
        val cell = cellSpace.cell
        val neighborInfoList = container.queryNeighbors(cellSpace)
        val neighborCells = neighborInfoList.map { it.neighborInfo.cell }.toHashSet()

        // Stop all running simulations

        neighborCells.map { it.graph }.distinct().forEach {
            it.stopSimulation()
        }

        /*
        * Cases:
        *   1. We don't have any neighbors. We must create a new circuit.
        *   2. We have a single neighbor. We can add this cell to their circuit.
        *   3. We have multiple neighbors, but they are part of the same circuit. We can add this cell to the common circuit.
        *   4. We have multiple neighbors, and they are part of different circuits. We need to create a new circuit,
        *       that contains the cells of the other circuits, plus this one.
        * */

        // This is common logic for all cases

        cell.connections = ArrayList(neighborInfoList.map { CellConnectionInfo(it.neighborInfo.cell, it.sourceDirection) })

        neighborInfoList.forEach { neighborInfo ->
            Eln2.LOGGER.info("Neighbor $neighborInfo")

            neighborInfo.neighborInfo.cell.connections.add(CellConnectionInfo(cell, neighborInfo.neighborDirection))
            neighborInfo.neighborContainer.recordConnection(
                neighborInfo.neighborInfo,
                neighborInfo.neighborDirection,
                cellSpace
            )

            container.recordConnection(cellSpace, neighborInfo.sourceDirection, neighborInfo.neighborInfo)
        }

        if (neighborInfoList.isEmpty()) {
            // Case 1. Create new circuit

            val graph = manager.createGraph()

            graph.addCell(cell)
        } else if (haveCommonCircuit(neighborInfoList)) {
            // Case 2 and 3. Join the existing circuit.

            val graph = neighborInfoList[0].neighborInfo.cell.graph

            graph.addCell(cell)

            // Add this cell to the neighbors' connections.

            neighborInfoList.forEach { neighborInfo ->
                neighborInfo.neighborInfo.cell.update(connectionsChanged = true, graphChanged = false)
            }
        } else {
            // Case 4. We need to create a new circuit, with all cells and this one.

            // Identify separate circuits.
            val disjointGraphs = neighborInfoList.map { it.neighborInfo.cell.graph }.distinct()

            // Create new circuit.
            val graph = manager.createGraph()

            // Register current cell

            graph.addCell(cell)

            // Copy cells over to the new circuit and destroy previous circuits.
            disjointGraphs.forEach { existingGraph ->
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

        cell.graph.buildSolver()

        // This cell had a complete update.
        cell.update(connectionsChanged = true, graphChanged = true)
        cell.container?.topologyChanged()

        cell.graph.startSimulation()
    }

    /**
     * Checks whether the cells share the same graph.
     * @return True, if the specified cells share the same graph. Otherwise, false.
     * */
    private fun haveCommonCircuit(neighbors: ArrayList<CellNeighborInfo>): Boolean {
        if (neighbors.size < 2) {
            return true
        }

        val graph = neighbors[0].neighborInfo.cell.graph

        neighbors.drop(1).forEach { info ->
            if (info.neighborInfo.cell.graph != graph) {
                return false
            }
        }

        return true
    }

    /**
     * Rebuilds the topology of a graph, presumably after a cell has been removed.
     * This will handle cases such as the graph splitting, because a cut vertex was removed.
     * This is a performance intensive operation, because it is likely to perform a search through the cells.
     * There is a case, though, that will complete in constant time: removing a cell that has zero or one neighbors.
     * Keep in mind that the simulation logic likely won't complete in constant time, in any case.
     * */
    private fun rebuildTopologies(
        neighborInfoList: ArrayList<CellNeighborInfo>,
        removedCell: CellBase,
        manager: CellGraphManager
    ) {
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

        val neighbors = neighborInfoList.map { it.neighborInfo.cell }.toHashSet()
        val neighborQueue = ArrayDeque<CellBase>()
        neighborQueue.addAll(neighbors)

        val bfsVisited = HashSet<CellBase>()
        val bfsQueue = ArrayDeque<CellBase>()

        while (neighborQueue.size > 0) {
            val neighbor = neighborQueue.removeFirst()

            // Create new circuit for all cells connected to this one.
            val graph = manager.createGraph()

            // Start BFS at the neighbor.
            bfsQueue.add(neighbor)

            while (bfsQueue.size > 0) {
                val cell = bfsQueue.removeFirst()

                if (!bfsVisited.add(cell)) {
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
                cell.connections.forEach { connectedCellInfo ->
                    // This must be handled above.
                    assert(connectedCellInfo.cell != removedCell)

                    bfsQueue.add(connectedCellInfo.cell)
                }
            }

            assert(bfsQueue.isEmpty())

            // Refit cells
            graph.cells.forEach { cell ->
                val isNeighbor = neighbors.contains(cell)

                cell.update(connectionsChanged = isNeighbor, graphChanged = true)
                cell.container?.topologyChanged()
            }

            // Finally, build the solver and start simulation.

            graph.buildSolver()
            graph.startSimulation()

            // We don't need to keep the cells, we have already traversed all the connected ones.
            bfsVisited.clear()
        }
    }
}
