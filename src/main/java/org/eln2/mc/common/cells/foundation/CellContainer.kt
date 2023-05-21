package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction

interface ICellContainer {
    fun getCells(): ArrayList<CellBase>
    fun queryNeighbors(actualCell: CellBase): ArrayList<CellNeighborInfo>

    fun recordConnection(actualCell: CellBase, remoteCell: CellBase)

    fun topologyChanged()

    val manager: CellGraphManager
}

/**
 * Represents a query into a Cell Container. Currently, queries are used to determine cell connection candidates.
 * @param connectionFace The face at the boundary between the two containers. It can be thought of as the common face. Implicitly, this is the contact face of the container that is being queried. It is not implied that a cell exists on this face, but rather that it may connect via this face.
 * @param surface This is the placement face of the cell. It is used to determine whether a connection is viable for certain connection modes. As an example, take the planar connection. It will only allow connections between cells that are mounted on the same face (plane) in the two containers.
 * */
data class CellQuery(val connectionFace: Direction, val surface: Direction)

/**
 * Encapsulates information about a neighbor cell.
 * */
data class CellNeighborInfo(
    val neighbor: CellBase,
    val neighborContainer: ICellContainer
)
