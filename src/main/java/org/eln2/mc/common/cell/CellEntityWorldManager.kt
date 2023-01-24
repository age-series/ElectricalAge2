package org.eln2.mc.common.cell

import net.minecraft.server.level.ServerLevel
import org.eln2.mc.common.blocks.CellBlockEntity

object CellEntityWorldManager {
    fun place(entity : CellBlockEntity) : CellBase{
        val provider = entity.cellProvider

        // Create the cell based on the provider.
        val cell = provider.create(entity.pos)

        cell.tile = entity
        cell.id = provider.registryName!!

        //val direction = PlacementRotation (entity.state.getValue(HorizontalDirectionalBlock.FACING))

        val graphManager = CellGraphManager.getFor(entity.level as ServerLevel)

        entity.graphManager = graphManager

        // Handle updating/creating graphs using the new cell.
        registerCell(cell)

        // Notify the cell of the new placement.
        cell.setPlaced()

        // Finally, build the solver.
        cell.graph.build()

        return cell
    }

    fun destroy(entity : CellBlockEntity){
        val cell = entity.cell!!

        removeCell(cell)

        cell.destroy()
    }

    private fun removeCell(cell : CellBase){
        val entity = cell.tile!!
        val manager = entity.graphManager
        val neighborCells = entity.getNeighborCells()

        val graph = cell.graph

        // This is common logic for all cases.
        neighborCells.forEach { neighbor ->
            neighbor.connections.remove(cell)
        }

        /*
        * Cases:
        *   1. We don't have any neighbors. We can destroy the circuit.
        *   2. We have a single neighbor. We can remove ourselves from the circuit.
        *   3. We have multiple neighbors, and we are not a cut vertex. We can remove ourselves from the circuit.
        *   4. We have multiple neighbors, and we are a cut vertex. We need to remove ourselves, find the new disjoint graphs,
        *        and rebuild the circuits.
        * */

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

            val neighbor = neighborCells[0]

            neighbor.update(connectionsChanged = true, graphChanged = false)
        }
        else{
            // Case 3 and 4. Implement a more sophisticated algorithm, if necessary.

            graph.destroy()
            rebuildTopologies(neighborCells, cell, manager)
        }
    }

    private fun registerCell(cell : CellBase){
        val entity = cell.tile!!
        val manager = entity.graphManager
        val neighborCells = entity.getNeighborCells()

        /*
        * Cases:
        *   1. We don't have any neighbors. We must create a new circuit.
        *   2. We have a single neighbor. We can add this cell to their circuit.
        *   3. We have multiple neighbors, but they are part of the same circuit. We can add this cell to the common circuit.
        *   4. We have multiple neighbors, and they are part of different circuits. We need to create a new circuit,
        *       that contains the cells of the other circuits, plus this one.
        * */

        // This is common logic for all cases

        cell.connections = neighborCells

        neighborCells.forEach { neighbor ->
            neighbor.connections.add(cell)
        }

        if(neighborCells.isEmpty()){
            // Case 1. Create new circuit

            val graph = manager.createGraph()
            graph.addCell(cell)
        }
        else if(haveCommonCircuit(neighborCells)){
            // Case 2 and 3. Join the existing circuit.

            val graph = neighborCells[0].graph

            graph.addCell(cell)

            // Add this cell to the neighbors' connections.

            neighborCells.forEach { neighborCell ->
                neighborCell.update(connectionsChanged = true, graphChanged = false)
            }
        }
        else{
            // Case 4. We need to create a new circuit, with all cells and this one.

            // Identify separate circuits.
            val disjointGraphs = neighborCells.map { it.graph }.distinct()

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
                }

                existingGraph.destroy()
            }
        }

        // This cell had a complete update.
        cell.update(connectionsChanged = true, graphChanged = true)
    }

    private fun haveCommonCircuit(neighbors : ArrayList<CellBase>) : Boolean{
        if(neighbors.size < 2){
            return true
        }

        val graph = neighbors[0].graph

        neighbors.drop(1).forEach {cell ->
            if(cell.graph != graph){
                return false
            }
        }

        return true
    }

    private fun rebuildTopologies(neighbors: ArrayList<CellBase>, removedCell : CellBase, manager: CellGraphManager){
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
            }

            // Finally, build the solver.

            graph.build()

            // We don't need to keep the cells, we have already traversed all the connected ones.
            bfsVisited.clear()
        }
    }
}
