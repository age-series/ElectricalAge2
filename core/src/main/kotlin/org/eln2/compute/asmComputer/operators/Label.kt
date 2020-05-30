package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.Operator

/**
 * Label - just a label! It requires a literal text string, eg `labl "ThisThing"`. Costs 1 tick because it does.
 */
class Label : Operator() {
    override val OPCODE = "labl"
    override val MIN_ARGS = 1
    override val MAX_ARGS = 1
    override val COST = 0.0

    /**
     * run - It literally does nothing.
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {}
}
