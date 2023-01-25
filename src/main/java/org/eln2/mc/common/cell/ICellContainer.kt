package org.eln2.mc.common.cell

import net.minecraft.core.Direction
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellGraphManager

/**
 * Cell container
 *  - Holds a set of cells that exist in a block space
 *  - Implemented by block entities
 *  - Is used by the connection logic to form connections with cells from different block entities
 *  - Example:
 *      1. A furnace
 *          Such a machine would contain a cell (a ballast) that drains power from the network.
 *          getCells would return the ballast cell, and getNeighbors would look for cells adjacent to the power input side.
 *      2. A multipart container
 *          The multipart container is a block entity that stores "parts", which represent some smaller components
 *          (e.g. wires) that exist in the same block space.
 *          There would be one part per inner face, and getNeighbors would look for other parts and cells in other containers.
 *
 * */
interface ICellContainer {
    /**
     * @return A list of cells in this container.
     * */
    fun getCells() : ArrayList<CellBase>

    /**
     * @param direction The side of the container.
     * @return The cell on that face of the container, or null.
     * */
    fun getCell(direction : Direction) : CellBase?

    /**
     * @param cell The cell to scan for. It must be a cell from within this container.
     * @return The neighbors for the specified cell.
     * */
    fun getNeighbors(cell : CellBase) : ArrayList<CellBase>

    /**
     * The manager responsible for the cells of this container (per dimension)
     * */
    val manager : CellGraphManager
}
