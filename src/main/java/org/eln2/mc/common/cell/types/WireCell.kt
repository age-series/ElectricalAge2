package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellBase

class WireCell(pos : BlockPos) : CellBase(pos) {
    override fun tileLoaded(){
        Eln2.LOGGER.info("Wire loaded $pos")
    }

    override fun tileUnloaded() {
        Eln2.LOGGER.info("Wire unloaded $pos")
    }

    override fun completeDiskLoad() {
        Eln2.LOGGER.info("Wire completed disk load $pos")
    }

    override fun setPlaced() {
        Eln2.LOGGER.info("Wire set placed $pos")
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {
        Eln2.LOGGER.info("Wire $pos update -> connections: $connectionsChanged graphs: $graphChanged")
    }
}
