package org.eln2.mc.common.cells

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.cells.objects.VoltageSourceObject

class VoltageSourceCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(VoltageSourceObject())
    }
}
