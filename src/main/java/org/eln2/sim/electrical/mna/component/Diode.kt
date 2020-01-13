package org.eln2.sim.electrical.mna.component

class Diode: DynamicResistor() {
    override var name = "d"
    override val nodeCount = 2
    
    var minR = 1e-3
    var maxR = 1e10

    override fun simStep() {
        // Theorem: changing the resistance should never lead to a change in sign of the current for a *SINGLE* timestep
        // as long as that is valid, this won't oscillate:
        println("D.sS: in u=$u r=$r")
        if(u > 0) {
            if(r > minR) r = minR
        } else {
            if(r < maxR) r = maxR
        }
        println("D.sS: out u=$u r=$r")
    }
}