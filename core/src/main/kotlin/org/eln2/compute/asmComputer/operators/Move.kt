package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.*

open class Move: Operator() {
	override val OPCODE = "move"
	override val MIN_ARGS = 2
	override val MAX_ARGS = 2
	override val COST = 0.0
	override fun run(opList: List<String>, asmComputer: AsmComputer) {
		if (opList.size != 2) {
			asmComputer.currState = State.Errored
			asmComputer.currStateReasoning = "Invalid move command: $opList"
			return
		}

		//println("Trying move: ${opList}")

		val outputClass = findRegisterType(opList[0], asmComputer)
		val inputClass = findRegisterType(opList[1], asmComputer)

		if (outputClass == null) {
			asmComputer.currState = State.Errored
			asmComputer.currStateReasoning = "First argument ${opList[0]} not a register"
			return
		}

		if (inputClass == null) {
			when(outputClass) {
				IntRegister::class, ReadOnlyIntRegister::class -> {
					asmComputer.intRegisters[opList[0]]?.contents = opList[1].toIntOrNull()?: 0
					return
				}
				DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
					asmComputer.doubleRegisters[opList[0]]!!.contents = opList[1].toDoubleOrNull()?: 0.0
					return
				}
				StringRegister::class, ReadOnlyStringRegister::class -> {
					val spl = opList[1].split("\"")
					if (spl.size != 3) return
					// The big string replace allows \ to be used to escape a space. The string must be in double quotes.
					asmComputer.stringRegisters[opList[0]]?.contents = spl[1].replace("\\"," ").replace("\\ \\", "\\")
					return
				}
				else -> {
					asmComputer.currState = State.Errored
					asmComputer.currStateReasoning = "Not sure what type this register is: ${opList[0]}"
					return
				}
			}
		}


		when (outputClass) {
			IntRegister::class, ReadOnlyIntRegister::class -> {
				when(inputClass) {
					IntRegister::class, ReadOnlyIntRegister::class -> {
						asmComputer.intRegisters[opList[0]]?.contents = asmComputer.intRegisters[opList[1]]?.contents?: 0
						return
					}
					DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
						asmComputer.intRegisters[opList[0]]?.contents = (asmComputer.doubleRegisters[opList[1]]?.contents?: 0.0).toInt()
						return
					}
					StringRegister::class, ReadOnlyStringRegister::class -> {
						asmComputer.intRegisters[opList[0]]?.contents = (asmComputer.stringRegisters[opList[1]]?.contents?: "").toIntOrNull()?: 0
						return
					}
					else -> {
						asmComputer.currState = State.Errored
						asmComputer.currStateReasoning = "Not sure what type this register is: ${opList[0]}"
						return
					}
				}
			}
			DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
				when(inputClass) {
					IntRegister::class, ReadOnlyIntRegister::class -> {
						asmComputer.doubleRegisters[opList[0]]?.contents = (asmComputer.intRegisters[opList[1]]?.contents?: 0).toDouble()
						return
					}
					DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
						asmComputer.doubleRegisters[opList[0]]?.contents = (asmComputer.doubleRegisters[opList[1]]?.contents?: 0.0)
						return
					}
					StringRegister::class, ReadOnlyStringRegister::class -> {
						asmComputer.doubleRegisters[opList[0]]?.contents = (asmComputer.stringRegisters[opList[1]]?.contents?: "").toDoubleOrNull()?: 0.0
						return
					}
					else -> {
						asmComputer.currState = State.Errored
						asmComputer.currStateReasoning = "Not sure what type this register is: ${opList[0]}"
						return
					}
				}
			}
			StringRegister::class, ReadOnlyStringRegister::class -> {
				when(inputClass) {
					IntRegister::class, ReadOnlyIntRegister::class -> {
						asmComputer.stringRegisters[opList[0]]?.contents = (asmComputer.intRegisters[opList[1]]?.contents?: 0).toString()
						return
					}
					DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
						asmComputer.stringRegisters[opList[0]]?.contents = (asmComputer.doubleRegisters[opList[1]]?.contents?: 0.0).toString()
						return
					}
					StringRegister::class, ReadOnlyStringRegister::class -> {
						asmComputer.stringRegisters[opList[0]]?.contents = (asmComputer.stringRegisters[opList[1]]?.contents?: "")
						return
					}
					else -> {
						asmComputer.currState = State.Errored
						asmComputer.currStateReasoning = "Not sure what type this register is: ${opList[0]}"
						return
					}
				}
			}
			else -> {
				asmComputer.currState = State.Errored
				asmComputer.currStateReasoning = "Not sure what type this register is: ${opList[0]}"
				return
			}
		}
	}
}
