package org.eln2.mc.data

import org.eln2.mc.isLetter

data class StringScanner(val string: String) {
    var i = 0
        private set

    fun bind() = StringScanner(string).also { it.i = this.i }

    val eof get() = i >= string.length
    fun peek() = if (eof) error("Tried to peek at EOF") else string[i]
    fun pop() = if (eof) error("Tried to pop at EOF") else string[i++]
    fun popString(n: Int): String {
        val array = CharArray(n)

        repeat(n) {
            array[it] = pop()
        }

        return String(array)
    }

    fun popStringWhile(condition: (Char) -> Boolean): String {
        val sb = StringBuilder()

        while (!eof) {
            val curr = peek()

            if (!condition(curr)) {
                break
            }

            pop()
            sb.append(curr)
        }

        return sb.toString()
    }

    fun popInteger(): Int? {
        val str = popStringWhile { it.isDigit() }
        return if (str.isEmpty()) null
        else str.toInt()
    }

    fun popLetters() = popStringWhile { it.isLetter }

    fun matchPeek(x: String): Boolean {
        val matchScan = x.scanner()

        var i = this.i
        fun eof() = i >= string.length
        fun peek(): Char =
            if (eof()) error("Unexpected peek in match") /*we checked for EOF in the loop, why did this happen?*/ else string[i]

        fun pop() = peek().also { i++ }

        while (true) {
            if (matchScan.eof) {
                return true
            }

            if (eof()) {
                return false
            }

            if (matchScan.pop() != pop()) {
                return false
            }
        }
    }

    fun matchPop(x: String): Boolean {
        if (matchPeek(x)) {
            i += x.length
            return true
        }

        return false
    }
}

fun String.scanner() = StringScanner(this)

fun StringScanner.ifNotEof(vararg actions: (StringScanner) -> Unit) {
    for (action in actions) {
        if (this.eof) {
            return
        }

        action(this)
    }
}
