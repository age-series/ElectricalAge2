package org.eln2.mc.content.cell.solar_light

import net.minecraft.core.BlockPos
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ElectricalComponentConnection

class SolarLightCell(pos : BlockPos) : CellBase(pos) {

    override fun clearForRebuild() {}

    override fun componentForNeighbour(neighbour: CellBase): ElectricalComponentConnection { error("Lol, this doesn't do anything here") }

    override fun buildConnections() {}

    override fun getHudMap(): Map<String, String> {
        return mapOf()
    }
}
