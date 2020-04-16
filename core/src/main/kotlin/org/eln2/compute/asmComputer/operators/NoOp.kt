package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.Operator

open class NoOp: Operator() {
	override val OPCODE = "noop"
	override val MIN_ARGS = 0
	override val MAX_ARGS = 0
	override val COST = 0.0
	override fun run(opList: List<String>, asmComputer: AsmComputer) {}
}
