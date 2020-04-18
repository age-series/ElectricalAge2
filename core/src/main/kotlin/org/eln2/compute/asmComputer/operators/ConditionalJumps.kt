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
						val intA = getIntFromRegisterOrLiteral(opList[0], asmComputer)
						val intB = getIntFromRegisterOrLiteral(opList[1], asmComputer)
						if (intA == null || intB == null) {
							return invalidInstruction(opList, asmComputer)
						}
						result = compareInts(intA, intB)
					}
					else -> {
						return invalidInstruction(opList, asmComputer)
					}
				}
			}
			Double::class, DoubleRegister::class -> {
				when (bType) {
					Double::class, DoubleRegister::class -> {
						val doubleA = getDoubleFromRegisterOrLiteral(opList[0], asmComputer)
						val doubleB = getDoubleFromRegisterOrLiteral(opList[1], asmComputer)
						if (doubleA == null || doubleB == null) {
							return invalidInstruction(opList, asmComputer)
						}
						result = compareDoubles(doubleA, doubleB)
					}
					else -> {
						return invalidInstruction(opList, asmComputer)
					}
				}
			}
			String::class, StringRegister::class -> {
				when (bType) {
					String::class, StringRegister::class -> {
						val strA = getStringFromRegisterOrLiteral(opList[0], asmComputer)
						val strB = getStringFromRegisterOrLiteral(opList[1], asmComputer)
						if (strA == null || strB == null) {
							return invalidInstruction(opList, asmComputer)
						}
						result = compareStrings(strA, strB)
					}
					else -> {
						return invalidInstruction(opList, asmComputer)
					}
				}
			}
			else -> {
				return invalidInstruction(opList, asmComputer)
			}
		}

		if (!result) return // don't jump

		if (opList[2].toIntOrNull() != null) {
			// go to a particular code pointer location
			asmComputer.ptr = (opList[2].toIntOrNull()?: 0) - 1
		}else{
			// go to a label
			val labelNameArray = opList[0].split("\"") // labels are quoted string literals.
			if (labelNameArray.size != 3) {
				asmComputer.currState = CState.Errored
				asmComputer.currStateReasoning = "Label ${opList[0]} is invalid"
			}
			val labelName = labelNameArray[1]
			val codeLines = asmComputer.stringRegisters[asmComputer.codeRegister]?.contents?.split("\n") ?: return
			var labelLocation = codeLines.indexOf("labl \"$labelName\"")
			if (labelLocation == -1) return // do nothing
			asmComputer.ptr = labelLocation - 1 // to the line before the label, since the ptr will be +1'd after op.
		}
	}

	//Compare two integers. Override this in the comparison operator
	abstract fun compareInts(a: Int, b: Int): Boolean
	//Compare two doubles. Override this in the comparison operator
	abstract fun compareDoubles(a: Double, b: Double): Boolean
	//Compare two strings. Override this in the comparison operator
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
	override val OPCODE = "jplt"
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
	override val OPCODE = "jpge"
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
	override val OPCODE = "jple"
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
	override val OPCODE = "jpeq"
	override fun compareInts(a: Int, b: Int): Boolean {
		return a == b
	}
	override fun compareDoubles(a: Double, b: Double): Boolean {
		// Well, not ever exactly, but how about within 0.0001?
		val range = 0.0001
		val lowestAcceptable = b - range
		val highestAcceptable = b + range
		//println("$lowestAcceptable < $a? < $highestAcceptable")
		return (a > lowestAcceptable) && (a < highestAcceptable)
	}
	override fun compareStrings(a: String, b: String): Boolean {
		return a == b
	}
}
