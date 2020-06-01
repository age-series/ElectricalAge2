package org.eln2.compute.asmComputer

/**
 * Operator
 *
 * There's no great way to make inheritance work properly without declaring this as a class or interface, and using
 * static functions as I would like to do are not inheritable... so you have to instantiate it just to instantiate it.
 *
 * All operators are to be four letters long
 */
abstract class Operator {
    /**
     * The actual operation code for this operator
     */
    abstract val OPCODE: String

    /**
     * Minimum arguments for this operator
     */
    abstract val MIN_ARGS: Int

    /**
     * Maximum arguments for this operator
     */
    abstract val MAX_ARGS: Int

    /**
     * Cost: A theoretical cost of this operation, in joules.
     */
    abstract val COST: Double

    /**
     * The operation code parser should call into this for the actual stuff to do
     *
     * @param opString A list of operators, not including the actual operator opcode
     * @param asmComputer The computer instance we're running on
     */
    fun validateThenRun(opString: String, asmComputer: AsmComputer) {
        val argList = opString.split(" ").filter { it.isNotBlank() }
        if (argList.size > MAX_ARGS || argList.size < MIN_ARGS) {
            asmComputer.currState = CState.Errored
            asmComputer.currStateReasoning = "Invalid number of arguments: ${argList.size}, $argList"
        }
        this.run(opList = argList, asmComputer = asmComputer)
    }

    /**
     * run: Never call this directly (use validateThenRun), but this is where you implement your instruction
     *
     * @param opList A list of operators, not including the actual operator opcode.
     * @param asmComputer The computer instance we're running on.
     */
    abstract fun run(opList: List<String>, asmComputer: AsmComputer)

    /**
     * detectType: Detects the type of the string passed in. It will check the list of registers on the computer to see
     * if it is one of those registers before trying to parse a local.
     *
     * @param s The register or literal you want the type from
     * @param asmComputer The computer that you are using
     * @return Class type: either a Register or Int, Double, String
     */
    fun detectType(s: String, asmComputer: AsmComputer): Any? {
        val reg = findRegisterType(s, asmComputer)
        if (reg != null) {
            return reg
        }
        // must be a literal
        if (s.toIntOrNull() != null && !s.contains(".")) {
            return Int
        }
        if (s.toDoubleOrNull() != null && s.contains(".")) {
            return Double
        }
        // TODO: Check if the string is in literal format (requires 2 quotes)
        return String
    }

    /**
     * Given a register name, find the register's Class type
     * @param register name to search for
     * @param asmComputer computer to search on
     * @return The register type or null if not found
     */
    fun findRegisterType(register: String, asmComputer: AsmComputer): Any? {
        if (register in asmComputer.intRegisters) return IntRegister::class
        if (register in asmComputer.doubleRegisters) return DoubleRegister::class
        if (register in asmComputer.stringRegisters) return StringRegister::class
        return null
    }

    /**
     * Gets an integer value from the register or literal
     * @param argument The argument to the operator
     * @param asmComputer The computer you are using
     * @return The integer or null if not found/parsable
     */
    fun getIntFromRegisterOrLiteral(s: String, asmComputer: AsmComputer): Int? {
        return if (s in asmComputer.intRegisters) {
            asmComputer.intRegisters[s]?.contents
        } else {
            s.toIntOrNull()
        }
    }

    /**
     * Gets a double value from the register or literal
     * @param argument The argument to the operator
     * @param asmComputer The computer you are using
     * @return The double or null if not found/parsable
     */
    fun getDoubleFromRegisterOrLiteral(s: String, asmComputer: AsmComputer): Double? {
        return if (s in asmComputer.doubleRegisters) {
            asmComputer.doubleRegisters[s]?.contents
        } else {
            s.toDoubleOrNull()
        }
    }

    /**
     * Gets a string value from the register or literal
     * @param argument The argument to the operator
     * @param asmComputer The computer you are using
     * @return The string or null if not found/parsable
     */
    fun getStringFromRegisterOrLiteral(s: String, asmComputer: AsmComputer): String? {
        return if (s in asmComputer.stringRegisters) {
            asmComputer.stringRegisters[s]?.contents
        } else {
            val split = s.split("\"")
            if (split.size >= 3) {
                split.drop(0).drop(split.size)
                return split.joinToString("\"")
            }
            return null
        }
    }

    /**
     * Invalid Instruction
     * Call this if the instruction was invalidly formatted.
     * @param opList The operator list to the instruction
     * @param asmComputer The computer you are using
     */
    fun invalidInstruction(opList: List<String>, asmComputer: AsmComputer) {
        asmComputer.currState = CState.Errored
        asmComputer.currStateReasoning = "Invalid arguments to comparison: $opList"
    }
}
