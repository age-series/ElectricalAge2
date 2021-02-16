package org.eln2.sim.electrical.component

import org.eln2.sim.electrical.mna.component.Resistor

/**
 * Implements a simple SPST (Single Pole, Single Throw) switch.
 */
class Switch: Resistor() {
    override val imageName = "switch"

    // You can use open or closed but open is the actual backing var here.
    var open = true
    var closed: Boolean
        get() = !open
        set(v) {
            open = !v
        }

    // closedResistance is when the switch is closed
    var closedResistance = 1.0
    // openResistance is when the switch is open
    var openResistance = 100_000_000.0

    override var resistance: Double
        get() {
            return if (open) {
                openResistance
            } else {
                closedResistance
            }
        }
        set(v) {
            if (v > 0) {
                closedResistance = v
            }
        }

    override fun detail(): String {
        return "[switch $name: ${potential}v, ${current}A, open: ${openResistance}Ω, closed: ${closedResistance}Ω, ${power}W]"
    }

    fun toggle() {
        open = !open
    }
}
