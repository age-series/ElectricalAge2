package org.eln2.mc.common.cells

import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo

class SolarLightCell(pos : CellPos) : CellBase(pos) {

    override fun clear() {}

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo { error("Lol, this doesn't do anything here") }

    override fun buildConnections() {}

    override fun getHudMap(): Map<String, String> {
        return mapOf()
    }
}
