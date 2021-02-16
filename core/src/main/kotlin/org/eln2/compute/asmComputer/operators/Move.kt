package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.CState
import org.eln2.compute.asmComputer.DoubleRegister
import org.eln2.compute.asmComputer.IntRegister
import org.eln2.compute.asmComputer.Operator
import org.eln2.compute.asmComputer.ReadOnlyDoubleRegister
import org.eln2.compute.asmComputer.ReadOnlyIntRegister
import org.eln2.compute.asmComputer.ReadOnlyStringRegister
import org.eln2.compute.asmComputer.StringRegister

/**
 * Move - this will not only move data from registers that are similar, but it will also convert data from one
 * register type to another. It's pretty powerful.
 */
open class Move : Operator() {
    override val OPCODE = "move"
    override val MIN_ARGS = 2
    override val MAX_ARGS = 2
    override val COST = 0.0

    /**
     * run
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {
        val outputClass = findRegisterType(opList[0], asmComputer)
        val inputClass = findRegisterType(opList[1], asmComputer)

        if (outputClass == null) {
            asmComputer.currState = CState.Errored
            asmComputer.currStateReasoning = "First argument ${opList[0]} not a register"
            return
        }

        if (inputClass == null) {
            copyLiteralToRegister(opList[0], opList[1], asmComputer, outputClass)
        } else {
            copyRegisterToRegister(opList[0], outputClass, opList[1], inputClass, asmComputer)
        }
    }

    /**
     * This copies a literal into a register
     * @param register the register to copy into
     * @param literal the literal to read from
     * @param computer our computer instance
     * @param type the type of register
     */
    private fun copyLiteralToRegister(register: String, literal: String, asmComputer: AsmComputer, type: Any?) {
        when (type) {
            IntRegister::class, ReadOnlyIntRegister::class -> {
                asmComputer.intRegisters[register]?.contents = literal.toIntOrNull() ?: 0
                return
            }
            DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
                asmComputer.doubleRegisters[register]!!.contents = literal.toDoubleOrNull() ?: 0.0
                return
            }
            StringRegister::class, ReadOnlyStringRegister::class -> {
                val spl = literal.split("\"")
                if (spl.size != 3) return
                // The big string replace allows \ to be used to escape a space. The string must be in double quotes.
                asmComputer.stringRegisters[register]?.contents = spl[1].replace("\\", " ").replace("\\ \\", "\\")
                return
            }
            else -> {
                asmComputer.currState = CState.Errored
                asmComputer.currStateReasoning = "Not sure what type this register is: $register"
                return
            }
        }
    }

    /**
     * This copies one register into another
     * @param registerOut The register to copy to
     * @param registerOutType The type of the register to copy to
     * @param registerIn The register to copy from
     * @param registerInType The type of register to copy from
     * @param asmComputer Our computer instance
     */
    private fun copyRegisterToRegister(
        registerOut: String,
        registerOutType: Any?,
        registerIn: String,
        registerInType: Any?,
        asmComputer: AsmComputer
    ) {
        when (registerOutType) {
            IntRegister::class, ReadOnlyIntRegister::class -> {
                when (registerInType) {
                    IntRegister::class, ReadOnlyIntRegister::class -> {
                        asmComputer.intRegisters[registerOut]?.contents =
                            asmComputer.intRegisters[registerIn]?.contents ?: 0
                        return
                    }
                    DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
                        asmComputer.intRegisters[registerOut]?.contents =
                            (asmComputer.doubleRegisters[registerIn]?.contents ?: 0.0).toInt()
                        return
                    }
                    StringRegister::class, ReadOnlyStringRegister::class -> {
                        asmComputer.intRegisters[registerOut]?.contents =
                            (asmComputer.stringRegisters[registerIn]?.contents ?: "").toIntOrNull() ?: 0
                        return
                    }
                    else -> {
                        asmComputer.currState = CState.Errored
                        asmComputer.currStateReasoning = "Not sure what type this register is: $registerIn"
                        return
                    }
                }
            }
            DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
                when (registerInType) {
                    IntRegister::class, ReadOnlyIntRegister::class -> {
                        asmComputer.doubleRegisters[registerOut]?.contents =
                            (asmComputer.intRegisters[registerIn]?.contents ?: 0).toDouble()
                        return
                    }
                    DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
                        asmComputer.doubleRegisters[registerOut]?.contents =
                            (asmComputer.doubleRegisters[registerIn]?.contents ?: 0.0)
                        return
                    }
                    StringRegister::class, ReadOnlyStringRegister::class -> {
                        asmComputer.doubleRegisters[registerOut]?.contents =
                            (asmComputer.stringRegisters[registerIn]?.contents ?: "").toDoubleOrNull() ?: 0.0
                        return
                    }
                    else -> {
                        asmComputer.currState = CState.Errored
                        asmComputer.currStateReasoning = "Not sure what type this register is: $registerIn"
                        return
                    }
                }
            }
            StringRegister::class, ReadOnlyStringRegister::class -> {
                when (registerInType) {
                    IntRegister::class, ReadOnlyIntRegister::class -> {
                        asmComputer.stringRegisters[registerOut]?.contents =
                            (asmComputer.intRegisters[registerIn]?.contents ?: 0).toString()
                        return
                    }
                    DoubleRegister::class, ReadOnlyDoubleRegister::class -> {
                        asmComputer.stringRegisters[registerOut]?.contents =
                            (asmComputer.doubleRegisters[registerIn]?.contents ?: 0.0).toString()
                        return
                    }
                    StringRegister::class, ReadOnlyStringRegister::class -> {
                        asmComputer.stringRegisters[registerOut]?.contents =
                            (asmComputer.stringRegisters[registerIn]?.contents ?: "")
                        return
                    }
                    else -> {
                        asmComputer.currState = CState.Errored
                        asmComputer.currStateReasoning = "Not sure what type this register is: $registerIn"
                        return
                    }
                }
            }
            else -> {
                asmComputer.currState = CState.Errored
                asmComputer.currStateReasoning = "Not sure what type this register is: $registerOut"
                return
            }
        }
    }
}
