package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.Operator

/**
 * Just your basic do nothing
 */
open class NoOp : Operator() {
    override val OPCODE = "noop"
    override val MIN_ARGS = 0
    override val MAX_ARGS = 0
    override val COST = 0.0

    /**
     * run - It literally does nothing.
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {}
}
