package org.eln2.mc.common.cell

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.eln2.mc.common.blocks.CellBlockEntity
import java.util.*
import kotlin.collections.ArrayList

class CellEntityNetworkManager(val level : Level) {
    fun place(description: CellEntityDescription) : CellBase{
        val entity = description.entity
        val provider = description.provider

        // Create the cell based on the provider.
        val cell = provider.create(entity.pos)

        cell.tile = entity
        cell.id = provider.registryName!!

        //val direction = PlacementRotation (entity.state.getValue(HorizontalDirectionalBlock.FACING))

        val graphManager = CellGraphManager.getFor(level as ServerLevel)

        entity.graphManager = graphManager

        // Handle updating/creating graphs using the new cell.
        registerCell(entity, cell, graphManager)

        // Notify the cell of the new placement.
        cell.setPlaced()

        setChanged()

        // Finally, build the solver.
        cell.graph.build()
    }

    private fun registerCell(entity : CellBlockEntity, cell : CellBase, manager : CellGraphManager){
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
}
