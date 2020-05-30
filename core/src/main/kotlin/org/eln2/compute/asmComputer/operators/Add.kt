package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.CState
import org.eln2.compute.asmComputer.Operator

/**
 * addi: adds integers
 */
open class AddI : Operator() {
    override val OPCODE = "addi"
    override val MIN_ARGS = 2
    override val MAX_ARGS = Int.MAX_VALUE
    override val COST = 0.0

    /**
     * run
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {
        if (opList[0] !in asmComputer.intRegisters) {
            asmComputer.currState = CState.Errored
            asmComputer.currStateReasoning = "Nonexistent Register {}".format(opList[0])
            return
        }
        var result = asmComputer.intRegisters[opList[0]]?.contents ?: 0
        opList.drop(1).map {
            if (it in asmComputer.intRegisters) {
                asmComputer.intRegisters[it]?.contents ?: 0
            } else {
                val v = it.toIntOrNull()
                if (v == null) {
                    asmComputer.currState = CState.Errored
                    asmComputer.currStateReasoning = "{} is not a value or register"
                    return
                }
                v
            }
        }.forEach { result += it }
        asmComputer.intRegisters[opList[0]]?.contents = result
    }
}

/**
 * addd: adds doubles
 */
open class AddD : Operator() {
    override val OPCODE = "addd"
    override val MIN_ARGS = 2
    override val MAX_ARGS = Int.MAX_VALUE
    override val COST = 0.0

    /**
     * run
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {
        if (opList[0] !in asmComputer.doubleRegisters) {
            asmComputer.currState = CState.Errored
            asmComputer.currStateReasoning = "Nonexistent Register {}".format(opList[0])
            return
        }
        var result = asmComputer.doubleRegisters[opList[0]]?.contents ?: 0.0
        opList.drop(1).map {
            if (it in asmComputer.doubleRegisters) {
                asmComputer.doubleRegisters[it]?.contents ?: 0.0
            } else {
                val v = it.toDoubleOrNull()
                if (v == null) {
                    asmComputer.currState = CState.Errored
                    asmComputer.currStateReasoning = "{} is not a value or register"
                    return
                }
                v
            }
        }.forEach { result += it }
        asmComputer.doubleRegisters[opList[0]]?.contents = result
    }
}
