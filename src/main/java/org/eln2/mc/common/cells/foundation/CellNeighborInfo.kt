package org.eln2.mc.common.cells.foundation

import org.eln2.mc.common.space.RelativeRotationDirection

/**
 * Encapsulates information about a neighbor cell.
 * */
data class CellNeighborInfo(
    val neighborInfo : CellInfo,
    val neighborContainer : ICellContainer,

    /**
     * This is the direction from the source cell to the neighbor cell.
     * */
    val sourceDirection : RelativeRotationDirection,

    /**
     * This is the direction from the neighbor cell to the source cell.
     * _This is not necessarily the opposite of the source direction._
     * */
    val neighborDirection : RelativeRotationDirection
)
