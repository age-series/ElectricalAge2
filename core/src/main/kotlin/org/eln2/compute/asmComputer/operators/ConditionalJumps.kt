package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.*

abstract class ComparisonJump: Operator() {
	override val MIN_ARGS = 3
	override val MAX_ARGS = 3
	override val COST = 0.0
	override fun run(opList: List<String>, asmComputer: AsmComputer) {
		val aType = detectType(opList[0], asmComputer)
		val bType = detectType(opList[1], asmComputer)
		val result: Boolean
		when (aType) {
			Int::class, IntRegister::class -> {
				when (bType) {
					Int::class, IntRegister::class -> {
						result = compareInts(opList[0].toInt(), opList[1].toInt())
					}
					else -> {
						return
					}
				}
			}
			Double::class, DoubleRegister::class -> {
				when (bType) {
					Double::class, DoubleRegister::class -> {
						result = compareDoubles(opList[0].toDouble(), opList[1].toDouble())
					}
					else -> {
						return
					}
				}
			}
			String::class, StringRegister::class -> {
				when (bType) {
					String::class, StringRegister::class -> {
						result = compareStrings(opList[0], opList[1])
					}
					else -> {
						return
					}
				}
			}
			else -> {
				return
			}
		}
		if (!result) return

		if (opList[2].toIntOrNull() != null) {
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

	fun detectType(s: String, asmComputer: AsmComputer): Any? {
		val reg = findRegisterType(s, asmComputer)
		if (reg != null) {
			return reg
		}
		if (s.toIntOrNull() != null && !s.contains(".")) {
			return Int
		}
		if (s.toDoubleOrNull() != null && s.contains(".")) {
			return Double
		}
		return String
	}

	abstract fun compareInts(a: Int, b: Int): Boolean
	abstract fun compareDoubles(a: Double, b: Double): Boolean
	abstract fun compareStrings(a: String, b: String): Boolean
}

class JumpGreaterThan: ComparisonJump() {
	override val OPCODE = "jpgt"
	override fun compareInts(a: Int, b: Int): Boolean {
		return a > b
	}
	override fun compareDoubles(a: Double, b: Double): Boolean {
		return a > b
	}
	override fun compareStrings(a: String, b: String): Boolean {
		return a > b
	}
}

class JumpLessThan: ComparisonJump() {
	override val OPCODE = "jpgt"
	override fun compareInts(a: Int, b: Int): Boolean {
		return a < b
	}
	override fun compareDoubles(a: Double, b: Double): Boolean {
		return a < b
	}
	override fun compareStrings(a: String, b: String): Boolean {
		return a < b
	}
}

class JumpGreaterEquals: ComparisonJump() {
	override val OPCODE = "jpgt"
	override fun compareInts(a: Int, b: Int): Boolean {
		return a >= b
	}
	override fun compareDoubles(a: Double, b: Double): Boolean {
		return a >= b
	}
	override fun compareStrings(a: String, b: String): Boolean {
		return a >= b
	}
}

class JumpLessEquals: ComparisonJump() {
	override val OPCODE = "jpgt"
	override fun compareInts(a: Int, b: Int): Boolean {
		return a <= b
	}
	override fun compareDoubles(a: Double, b: Double): Boolean {
		return a <= b
	}
	override fun compareStrings(a: String, b: String): Boolean {
		return a <= b
	}
}

class JumpEquals: ComparisonJump() {
	override val OPCODE = "jpgt"
	override fun compareInts(a: Int, b: Int): Boolean {
		return a == b
	}
	override fun compareDoubles(a: Double, b: Double): Boolean {
		// Well, not ever exactly, but how about within 0.0001?
		val range = 0.0001
		return (a < b - range) && (a > b + range)
	}
	override fun compareStrings(a: String, b: String): Boolean {
		return a == b
	}
}
