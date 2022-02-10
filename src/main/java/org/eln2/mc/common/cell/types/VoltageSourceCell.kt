package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.apache.logging.log4j.LogManager
import org.eln2.mc.Eln2
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellProvider

class VoltageSourceCell(pos : BlockPos) : CellBase(pos) {
    override fun tileLoaded(){
        Eln2.LOGGER.info("Voltage source loaded $pos")
    }

    override fun tileUnloaded() {
        Eln2.LOGGER.info("Voltage source unloaded $pos")
    }

    override fun completeDiskLoad() {
        Eln2.LOGGER.info("Voltage source completed disk load $pos")
    }

    override fun setPlaced() {
        Eln2.LOGGER.info("Voltage source set placed $pos")
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {
        Eln2.LOGGER.info("Voltage source $pos update -> connections: $connectionsChanged graphs: $graphChanged")
    }
}
