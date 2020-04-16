package org.eln2.compute.asmComputer

import org.eln2.compute.asmComputer.operators.*

class AsmComputer {

	// used to store integers
	val intRegisters = mutableMapOf<String, Int>()

	// used to store doubles
	val doubleRegisters = mutableMapOf<String, Double>()

	// used to store strings
	val stringRegisters = mutableMapOf<String, StringBuffer>()

	// combination of all registers (for searching)
	val allRegisters: Map<String, Any> = intRegisters + doubleRegisters + stringRegisters

	// map of all possible operators by opcode
	val operators: MutableMap<String, Operator>

	// Current processing state
	var currState = State.Stopped
	// Why we are in this state if it's errored.
	var currStateReasoning = ""
	// The code to run
	var code = ""
	// The code to run, split by newline
	private var codeLines: List<String> = listOf()
	// The pointer to the line of code to run from codeLines
	var ptr = 0

	init {
		"abcdefgh".forEach { intRegisters["i$it"] = 0 }
		"xyz".forEach { doubleRegisters["d$it"] = 0.0 }
		"xyz".forEach { stringRegisters["s$it"] = StringBuffer(32) }
		operators = mutableMapOf()
		operators["noop"] = NoOp()
		operators["addi"] = AddI()
		operators["addd"] = AddD()
		operators["subi"] = SubI()
		operators["subd"] = SubD()
	}

	/**
	 * step: Complete a step of the ASM Computer. This will execute the code at PTR.
	 */
	fun step() {
		if (this.currState == State.Errored) return
		currState = State.Running
		currStateReasoning = ""
		codeLines = code.split("\n")
		if (codeLines.size < ptr) {
			currState = State.Stopped
			currStateReasoning = "End of code"
			ptr = 0
		}
		val currentOperation = codeLines[ptr]
		val fullSplit = currentOperation.split(" ")
		val opcode = fullSplit[0]
		val argList = fullSplit.drop(1)
		if (opcode in operators) {
			operators[opcode]?.validateThenRun(argList.joinToString(" "), this)
			if (currState == State.Errored) {
				println("Computer entered errored State: $currStateReasoning")
			}else{
				ptr += 1
			}
		} else {
			currState = State.Errored
			currStateReasoning = "Opcode not found: $opcode"
		}
	}
}

/**
 * States of our computer
 */
enum class State {
	/**
	 * The computer is running
	 */
	Running,

	/**
	 * The computer is stopped on an instruction (paused?)
	 */
	Stopped,

	/**
	 * An invalid instruction was passed.
	 */
	Errored,

	/**
	 * The computer is waiting for data
	 */
	Data,
}


class StringBuffer(
	/**
	 * size: Maximum size of the buffer
	 */
	val size: Int) {

	private var _contents: String = ""

	/**
	 * The actual data of the string is stored here. You can touch contents like a normal string, except that you can't
	 * set the data to be longer than the buffer size.
	 */
	var contents
		/**
		 * Get the contents
		 * @return Returns current contents
		 */
		get() = _contents
		/**
		 * Set the contents. Does nothing if the size is longer than size.
		 * @param contents The new contents to set
		 */
		set(contents) {
			// Only set the string if the size is smaller than the buffer we provide.
			if (contents.length <= size) _contents = contents
		}
}

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
		val argList = opString.split(" ")
		if (argList.size > MAX_ARGS || argList.size < MIN_ARGS) {
			asmComputer.currState = State.Errored
			asmComputer.currStateReasoning = "Invalid number of arguments"
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
}
