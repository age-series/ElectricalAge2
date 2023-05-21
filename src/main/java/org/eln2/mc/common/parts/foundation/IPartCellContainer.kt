package org.eln2.mc.common.parts.foundation

import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellProvider

/**
 * Represents a part that has a cell, and will join a graph.
 * */
interface IPartCellContainer {
    /**
     * This is the cell owned by the part.
     * */
    val cell: CellBase

    /**
     * Indicates if the cell is available (loaded).
     * */
    val hasCell: Boolean

    /**
     * @return The provider associated with the cell.
     * */
    val provider: CellProvider

    /**
     * Indicates whether this part allows planar connections.
     * @see PartConnectionMode.Planar
     * */
    val allowPlanarConnections: Boolean

    /**
     * Indicates whether if this part allows inner connections.
     * @see PartConnectionMode.Inner
     * */
    val allowInnerConnections: Boolean

    /**
     * Indicates if this part allows wrapped connections.
     * @see PartConnectionMode.Wrapped
     * */
    val allowWrappedConnections: Boolean

    /**
     * Called when this part receives a cell connection.
     * @param direction The local direction towards the remote cell.
     * */
    fun recordConnection(remote: CellBase)

    /**
     * Called when this part is disconnected from the remote cell.
     * @param direction The local direction towards the remote cell.
     * */
    fun recordDeletedConnection(remote: CellBase)
}
