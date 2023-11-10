package org.eln2.mc.common.content

import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import kotlin.math.absoluteValue
import kotlin.math.sin

class OscillatorCell(ci: CellCreateInfo) : Cell(ci) {
    @SimObject
    val source = VoltageSourceObject(this)

    var t = 0.0

    override fun subscribe(subs: SubscriberCollection) {
        subs.addPre(this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        source.potential = sin(t / 2.0).absoluteValue * 12.0

        t += dt
    }
}

class OscillatorPart(ci: PartCreateInfo) :
    CellPart<OscillatorCell, BasicPartRenderer>(ci, Content.OSCILLATOR_CELL.get()) {

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.BATTERY)
}
