package org.eln2.mc.common.cells

import mcp.mobius.waila.api.IPluginConfig
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.cells.objects.ResistorObject
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

class ResistorCell(pos: CellPos) : CellBase(pos), IWailaProvider {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ResistorObject())
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        if(hasGraph){
            builder.text("Graph", graph.id)
        }
        builder.text("Connections", connections.map { it.sourceDirection }.joinToString(" "))
    }
}
