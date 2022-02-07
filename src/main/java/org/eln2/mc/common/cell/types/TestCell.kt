package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.apache.logging.log4j.LogManager
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellProvider

class TestCell(pos : BlockPos) : CellBase(pos) {
    class Provider : CellProvider() {
        override fun create(pos: BlockPos): CellBase{
            return TestCell(pos)
        }

        override fun connectionPredicate(dir: Direction): Boolean {
            return true
        }
    }

    override fun tileLoaded(){
        LogManager.getLogger().info("Tile loaded $pos")
    }

    override fun tileUnloaded() {
        LogManager.getLogger().info("Tile unloaded $pos")
    }

    override fun completeDiskLoad() {
        LogManager.getLogger().info("Complete disk load $pos")
    }

    override fun setPlaced() {
        LogManager.getLogger().info("Set placed $pos")
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {
        LogManager.getLogger().info("Updated connections: $connectionsChanged graphs: $graphChanged")
    }
}
