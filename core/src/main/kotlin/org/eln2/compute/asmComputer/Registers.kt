package org.eln2.compute.asmComputer

/**
 * Int Register
 * @param v the starting value
 */
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

/**
 * Read Only Int Register
 * @param v the value it has
 */
class ReadOnlyIntRegister(v: Int = 0) : IntRegister(v) {
    override var contents: Int
        get() {
            return _contents
        }
        set(value) {
            // do nothing
        }
}

/**
 * Double Register
 * @param v the starting value
 */
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

/**
 * Read Only Double Register
 * @param v the value it has
 */
class ReadOnlyDoubleRegister(v: Double = 0.0) : DoubleRegister(v) {
    override var contents: Double
        get() {
            return _contents
        }
        set(value) {
            // do nothing
        }
}

/**
 * String Register
 * @param size The size of the register
 * @param v the starting value
 */
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

    /**
     * toString (with) Numbers
     * Prints out the line numbers of the string for each newline. Useful for code prints.
     */
    fun toStringNumbers(): String {
        val codeLines = contents.split("\n")
        var lines = ""
        for (x in codeLines.indices) {
            lines += "$x ${codeLines[x]}\n"
        }
        return lines
    }
}

/**
 * Read Only String Register
 * @param v the starting value
 */
class ReadOnlyStringRegister(v: String = "") : StringRegister(0, v) {
    override var contents: String
        get() {
            return _contents
        }
        set(value) {
            // do nothing
        }
}
