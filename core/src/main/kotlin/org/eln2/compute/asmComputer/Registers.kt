package org.eln2.compute.asmComputer


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
