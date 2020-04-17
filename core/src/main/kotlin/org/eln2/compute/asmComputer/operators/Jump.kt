package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.Operator
import org.eln2.compute.asmComputer.State

class Jump: Operator() {
	override val OPCODE = "jump"
	override val MIN_ARGS = 1
	override val MAX_ARGS = 1
	override val COST = 0.0
	override fun run(opList: List<String>, asmComputer: AsmComputer) {
		if (opList[0].toIntOrNull() != null) {
			// go to a particular code pointer location
			asmComputer.ptr = (opList[0].toIntOrNull()?: 0) - 2
		}else{
			// go to a label
			val labelNameArray = opList[0].split("\"") // labels are quoted string literals.
			if (labelNameArray.size != 3) {
				asmComputer.currState = State.Errored
				asmComputer.currStateReasoning = "Label ${opList[0]} is invalid"
			}
			val labelName = labelNameArray[1]
			val codeLines = asmComputer.stringRegisters[asmComputer.codeRegister]?.contents?.split("\n") ?: return
			var labelLocation = codeLines.indexOf("labl \"$labelName\"")
			if (labelLocation == -1) return // do nothing
			asmComputer.ptr = labelLocation - 1 // to the line before the label, since the ptr will be +1'd after op.
		}
	}
}
