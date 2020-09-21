package org.eln2.sim.electrical.component
/*

import org.eln2.sim.electrical.mna.NEGATIVE
import org.eln2.sim.electrical.mna.POSITIVE
import org.eln2.sim.electrical.mna.component.Component

class SwitchSPDT: Component() {
    val sw1 = Switch()
    val sw2 = Switch()

    // You can use open or closed but open is the actual backing var here.
    var open = true
        set(v) {
            sw1.open = v
            sw2.closed = v
            field = v
        }
    var closed: Boolean
        get() = !open
        set(v) {
            open = !v
        }

    // closedResistance is when the switch is closed
    var closedResistance = 1.0
        set(v) {
            sw1.closedResistance = v
            sw2.closedResistance = v
            field = v
        }
    // openResistance is when the switch is open
    var openResistance = 100_000_000.0
        set(v) {
            sw1.openResistance = v
            sw2.openResistance = v
            field = v
        }

    init {
        sw1.openResistance = openResistance
        sw2.openResistance = openResistance
        sw1.closedResistance = closedResistance
        sw2.closedResistance = closedResistance
    }

    override fun stamp() {
        sw1.stamp()
        sw2.stamp()
    }

    override val name = "SwitchSPDT"
    override val pinCount = 3

    override fun connect(nidx: Int, to: Component, tidx: Int) {
        when(nidx) {
            0 -> {
                sw1.connect(POSITIVE, to, tidx)
            }
            1 -> {
                sw1.connect(NEGATIVE, to, tidx)
                sw2.connect(NEGATIVE, to, tidx)
            }
            2 -> {
                sw2.connect(POSITIVE, to, tidx)
            }
            else -> {
                println("You tried to connect to pin $nidx, but you only find abundant air in it's place")
            }
        }
    }

    override fun detail(): String {
        return "[switch $name open: ${openResistance}Ω, closed: ${closedResistance}Ω]"
    }
}

enum class PinsSPDT(val pin: Int) {
    NORMALLY_OPEN(0),
    NORMALLY_CLOSED(1),
    COMMON(2)
}
*/
