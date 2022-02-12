package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellBase

class ResistorCell(pos : BlockPos) : CellBase(pos) {
    override fun tileLoaded(){
        Eln2.LOGGER.info("Resistor loaded $pos")
    }

    override fun tileUnloaded() {
        Eln2.LOGGER.info("Resistor unloaded $pos")
    }

    override fun completeDiskLoad() {
        Eln2.LOGGER.info("Resistor completed disk load $pos")
    }

    override fun setPlaced() {
        Eln2.LOGGER.info("Resistor set placed $pos")
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {
        Eln2.LOGGER.info("Resistor $pos update -> connections: $connectionsChanged graphs: $graphChanged")
    }
}
