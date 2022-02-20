package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo

class SolarLightCell(pos : BlockPos) : CellBase(pos) {

    override fun clearForRebuild() {}

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo { error("Lol, this doesn't do anything here") }

    override fun buildConnections() {}

    override fun getHudMap(): Map<String, String> {
        return mapOf()
    }
}
