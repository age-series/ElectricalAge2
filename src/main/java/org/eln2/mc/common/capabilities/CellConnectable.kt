package org.eln2.mc.common.capabilities

import org.eln2.mc.common.blocks.CellTileEntity

abstract class CellConnectable {
    /**
     *  Called when an adjacent cell would like to connect to this cell.
     *  @param entity The cell entity which is attempting to connect.
     *  @return true if the connection is accepted. Otherwise, false.
     */
    abstract fun connectionRequestFrom(entity : CellTileEntity) : Boolean
}
