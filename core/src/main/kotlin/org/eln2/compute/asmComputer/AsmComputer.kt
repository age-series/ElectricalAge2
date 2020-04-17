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
	var currState = State.Stopped
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
		CopyStringPart(), StringLength(), Label(), Jump())
		operators = mutableMapOf()

		operatorListing.forEach {
			operators[it.OPCODE] = it
		}
	}

	/**
	 * step: Complete a step of the ASM Computer. This will execute the code at PTR.
	 */
	fun step() {
		if (this.currState == State.Errored) return
		currState = State.Running
		currStateReasoning = ""
		if (codeRegister !in listOf("cra", "crb")) {
			currState = State.Errored
			currStateReasoning = "Code Register must be cra or crb, found $codeRegister"
		}
		val cra = stringRegisters["cra"]?.contents
		val crb = stringRegisters["crb"]?.contents
		val codeRegisters = mapOf(Pair("cra", cra), Pair("crb", crb))
			codeLines = (codeRegisters[codeRegister] ?: "").split("\n")
		if (codeLines.size < ptr) {
			println("end of code: ${codeLines.size} < $ptr")
			currState = State.Stopped
			currStateReasoning = "End of code"
			ptr = 0
			return
		}
		val currentOperation = codeLines[ptr]
		val fullSplit = currentOperation.split(" ")
		val opcode = fullSplit[0]
		val argList = fullSplit.drop(1)
		if (opcode in operators) {
			//println("Exec: $fullSplit")
			operators[opcode]?.validateThenRun(argList.joinToString(" "), this)
			if (currState == State.Errored) {
				println("Computer entered errored State: $currStateReasoning")
			}else{
				ptr += 1
			}
		} else {
			currState = State.Errored
			currStateReasoning = "Opcode not found: $opcode"
			print("Opcode not found: $opcode")
		}
	}
}

// States of our computer
enum class State {
	// The computer is running
	Running,

	// The computer is stopped on an instruction (paused?)
	Stopped,

	// An invalid instruction was passed.
	Errored,

	// The computer is waiting for data
	Data,
}

open class IntRegister(v: Int = 0) {
	var _contents: Int = v
	open var contents: Int
		get() {
			return _contents
		}
		set(value) {
			_contents = value
		}

	override fun toString(): String {
		return contents.toString()
	}
}
class ReadOnlyIntRegister(v: Int = 0): IntRegister(v) {
	override var contents: Int
		get() {
			return _contents
		}
		set(value) {
			// do nothing
		}
}
open class DoubleRegister(v: Double = 0.0) {
	var _contents: Double = v
	open var contents: Double
		get() {
			return _contents
		}
		set(value) {
			_contents = value
		}
	override fun toString(): String {
		return contents.toString()
	}
}
class ReadOnlyDoubleRegister(v: Double = 0.0): DoubleRegister(v) {
	override var contents: Double
	get() {
		return _contents
	}
	set(value) {
		// do nothing
	}
}
open class StringRegister(var size: Int, v: String = "") {
	var _contents: String = v
	open var contents: String
	get() {
		return _contents
	}
	set(value) {
		if (value.length <= size) _contents = value
	}
	override fun toString(): String {
		return contents
	}

	fun toStringNumbers(): String {
		val codeLines = contents.split("\n")
		var lines = ""
		for (x in codeLines.indices) {
			lines += "$x ${codeLines[x]}\n"
		}
		return lines
	}
}
class ReadOnlyStringRegister(v: String = ""): StringRegister(0, v) {
	override var contents: String
	get() {
		return _contents
	}
	set(value) {
		// do nothing
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
		val argList = opString.split(" ").filter {it.isNotBlank()}
		if (argList.size > MAX_ARGS || argList.size < MIN_ARGS) {
			asmComputer.currState = State.Errored
			asmComputer.currStateReasoning = "Invalid number of arguments: ${argList.size}"
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

	fun findRegisterType(register: String, asmComputer: AsmComputer): Any? {
		if (register in asmComputer.intRegisters) return IntRegister::class
		if (register in asmComputer.doubleRegisters) return DoubleRegister::class
		if (register in asmComputer.stringRegisters) return StringRegister::class
		return null
	}
}
