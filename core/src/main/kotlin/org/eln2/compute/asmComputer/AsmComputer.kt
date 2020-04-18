package org.eln2.compute.asmComputer

import org.eln2.compute.asmComputer.operators.*

class AsmComputer {

	// used to store integers
	val intRegisters: MutableMap<String, IntRegister> = mutableMapOf<String, IntRegister>()

	// used to store doubles
	val doubleRegisters: MutableMap<String, DoubleRegister> = mutableMapOf<String, DoubleRegister>()

	// used to store strings
	val stringRegisters: MutableMap<String, StringRegister> = mutableMapOf<String, StringRegister>()

	// map of all possible operators by opcode
	val operators: MutableMap<String, Operator>

	// Current processing state
	var currState = CState.Stopped
	// Why we are in this state if it's errored.
	var currStateReasoning = ""
	// The code to run
	var codeRegister = "cra"
	// The code to run, split by newline
	private var codeLines: List<String> = listOf()
	// The pointer to the line of code to run from codeLines
	var ptr = 0

	init {
		"abcdefgh".forEach { intRegisters["i$it"] = IntRegister() }
		"xyz".forEach { doubleRegisters["d$it"] = DoubleRegister() }
		"xyz".forEach { stringRegisters["s$it"] = StringRegister(1024) }
		"ab".forEach{ stringRegisters["cr$it"] = StringRegister(4096) }

		val operatorListing = listOf(NoOp(), AddI(), AddD(), SubI(), SubD(), Move(), CodeSwitch(),
		CopyStringPart(), StringLength(), Label(), Jump(), JumpGreaterThan(), JumpLessThan(), JumpGreaterEquals(),
		JumpLessEquals(), JumpEquals())
		operators = mutableMapOf()

		operatorListing.forEach {
			operators[it.OPCODE] = it
		}
	}

	/**
	 * step: Complete a step of the ASM Computer. This will execute the code at PTR.
	 */
	fun step() {
		if (this.currState == CState.Errored) {
			println("computer errored, not stepping: $currStateReasoning")
			return
		}
		currState = CState.Running
		currStateReasoning = ""
		if (codeRegister !in listOf("cra", "crb")) {
			currState = CState.Errored
			currStateReasoning = "Code Register must be cra or crb, found $codeRegister"
		}
		val cra = stringRegisters["cra"]?.contents
		val crb = stringRegisters["crb"]?.contents
		val codeRegisters = mapOf(Pair("cra", cra), Pair("crb", crb))
			codeLines = (codeRegisters[codeRegister] ?: "").split("\n")
		if (codeLines.size <= ptr || ptr < 0) {
			println("end of code: ${codeLines.size} < $ptr")
			currState = CState.Stopped
			currStateReasoning = "End of code"
			ptr = 0
			return
		}
		val currentOperation = codeLines[ptr]
		val fullSplit = currentOperation.trimStart().split(" ")
		val opcode = fullSplit[0]
		val argList = fullSplit.drop(1)
		if (opcode in operators) {
			//println("Exec: $fullSplit")
			operators[opcode]?.validateThenRun(argList.joinToString(" "), this)
			if (currState == CState.Errored) {
				println("Computer entered errored State: $currStateReasoning")
			}else{
				ptr += 1
			}
		} else {
			currState = CState.Errored
			currStateReasoning = "Opcode not found: $opcode"
			print("Opcode not found: $opcode")
		}
	}
}
