package org.eln2.mc.common.cell.container

import org.eln2.mc.common.RelativeRotationDirection

data class CellNeighborInfo(
    val neighborSpace : CellSpaceLocation,
    val neighborContainer : ICellContainer,

    // The direction to the neighbor:
    val sourceDirection : RelativeRotationDirection,
    val neighborDirection : RelativeRotationDirection)
