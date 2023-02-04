package org.eln2.mc.common.parts

import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellProvider

/**
 * Represents a part that is a member of the simulation network.
 * */
interface IPartCellContainer {
    /**
     * This is the cell owned by the part.
     * */
    val cell : CellBase

    /**
     * Indicates if the cell is available (loaded).
     * */
    val hasCell : Boolean

    /**
     * This is the provider associated with the cell.
     * */
    val provider : CellProvider

    /**
     * Indicates if this part allows planar connections.
     * These are connections that form between parts placed on the same plane.
     * */
    val allowPlanarConnections : Boolean

    /**
     * Indicates if this part allows inner / interior connections.
     * These are connections that form between parts in the same container.
     * The connection forms inside corners. Parts placed on parallel faces shall not be included in this scan.
     * */
    val allowInnerConnections : Boolean

    /**
     * Indicates if this part allows connections with cells placed on different faces of the block.
     * These connections wrap around the corner of the block.
     * */
    val allowWrappedConnections : Boolean

    /**
     * Called when this part receives a cell connection.
     * @param direction The local direction towards the remote cell.
     * */
    fun recordConnection(direction: RelativeRotationDirection)

    /**
     * Called when this part is disconnected from the remote cell.
     * @param direction The local direction towards the remote cell.
     * */
    fun recordDeletedConnection(direction: RelativeRotationDirection)
}
