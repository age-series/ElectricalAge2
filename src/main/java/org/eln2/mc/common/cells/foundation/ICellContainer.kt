package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import org.eln2.mc.common.space.RelativeRotationDirection

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
    fun getCells(): ArrayList<CellInfo>

    /**
     * @return A cell that matches the specified query, or null, if no cell matches.
     * */
    fun query(query: CellQuery): CellInfo?

    /**
     * Queries the neighbors of the specified cell.
     * @return A list of all the neighbors of the specified cell.
     * */
    fun queryNeighbors(location: CellInfo): ArrayList<CellNeighborInfo>

    /**
     * Checks if the specified cell accepts connection from the specified direction.
     * @param location The cell to check candidates for.
     * @param direction The global direction.
     * @return A relative rotation, if the connection is accepted. Otherwise, null.
     * */
    fun probeConnectionCandidate(location: CellInfo, direction: Direction): RelativeRotationDirection?

    /**
     * Called by the connection manager when a connection is made.
     * @param location The cell that received the new connection.
     * @param direction The local direction towards the remote cell.
     * */
    fun recordConnection(location: CellInfo, direction: RelativeRotationDirection, neighborSpace: CellInfo)

    /**
     * Called by the connection manager when a connection is destroyed.
     * @param location The cell whose connection was destroyed.
     * @param direction The local direction towards the remote cell.
     * */
    fun recordDeletedConnection(location: CellInfo, direction: RelativeRotationDirection)

    /**
     * Called by the connection manager when the graphs associated with this container have changed (completely different graph).
     * */
    fun topologyChanged()

    /**
     * The manager responsible for the cells in this container (per dimension)
     * */
    val manager: CellGraphManager
}
