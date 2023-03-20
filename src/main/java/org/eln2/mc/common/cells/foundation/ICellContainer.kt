package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.space.RelativeRotationDirection

/**
 * Cell container
 *  - Holds a set of cells that exist in a block space
 *  - Implemented by block entities
 *  - Is used by the connection logic to form connections with cells from different containers, and the same container (inner connections).
 *  - Cell containers are expected to react to connection changes (e.g. to update the visual representation of the device)
 *  - Cells are accessed via queries. The implementation is likely to access the game world, to scan for neighbors and match them against the query.
 *  @see CellQuery
 * */
interface ICellContainer {
    /**
     * @return A list of cells in this container.
     * */
    fun getCells(): ArrayList<CellInfo>

    /**
     * Performs a directed query. Only one cell can match a given query.
     * @return A cell that matches the specified query, or null, if no cell matches.
     * */
    fun query(query: CellQuery): CellInfo?

    /**
     * Queries the neighbors of the specified cell. These neighbors may be part of this container, or other containers.
     * It **must** be guaranteed that this method returns the same results, if the state is not changed.
     * @return A list of all the neighbors of the specified cell.
     * */
    fun queryNeighbors(location: CellInfo): ArrayList<CellNeighborInfo>

    /**
     * Checks if the specified cell accepts connection from the specified direction.
     * @param location The cell to check candidates for.
     * @param direction The global direction.
     * @param mode The requested connection mode.
     * @return A relative rotation, if the connection is possible. Otherwise, null.
     * */
    fun probeConnectionCandidate(location: CellInfo, direction: Direction, mode: ConnectionMode): RelativeRotationDirection?

    /**
     * Called by the connection manager when a connection is made. Containers may react to this in order to e.g. update the view.
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
     * Called by the connection manager when cells from this container have been moved to a different graph.
     * */
    fun topologyChanged()

    /**
     * The manager responsible for the cells in this container (per dimension).
     * */
    val manager: CellGraphManager
}
