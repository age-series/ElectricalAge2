package org.eln2.mc.data

import org.eln2.mc.isLetter
import org.eln2.mc.scientific.chemistry.data.ChemicalElement

data class StringScanner(val string: String) {
    var i = 0
        private set

    fun bind() = StringScanner(string).also { it.i = this.i }

    val eof get() = i >= string.length
    fun peek() = if(eof) error("Tried to peek at EOF") else string[i]
    fun pop() = if(eof) error("Tried to pop at EOF") else string[i++]
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

            if(!condition(curr)) {
                break
            }

            pop()
            sb.append(curr)
        }

        return sb.toString()
    }

    fun popInteger(): Int? {
        val str = popStringWhile { it.isDigit() }
        return if(str.isEmpty()) null
        else str.toInt()
    }

    fun popLetters() = popStringWhile { it.isLetter }

    fun matchPeek(x: String): Boolean {
        val matchScan = x.scanner()

        var i = this.i
        fun eof() = i >= string.length
        fun peek(): Char = if(eof()) error("Unexpected peek in match") /*we checked for EOF in the loop, why did this happen?*/else string[i]
        fun pop() = peek().also { i++ }

        while (true) {
            if(matchScan.eof) {
                return true
            }

            if(eof()) {
                return false
            }

            if(matchScan.pop() != pop()) {
                return false
            }
        }
    }

    fun matchPop(x: String): Boolean {
        if(matchPeek(x)) {
            i += x.length
            return true
        }

        return false
    }
}

fun String.scanner() = StringScanner(this)

fun StringScanner.popChemicalElement(): ChemicalElement? {
    if(this.eof) return null

    if(!this.peek().isLetter || !this.peek().isUpperCase()) return null
    val a = popString(1)

    if(this.eof) return ChemicalElement.bySymbol[a]

    if(this.peek().isLetter && this.peek().isLowerCase()) {
        val b = this.peek()
        val res = ChemicalElement.bySymbol[a + b]

        if(res != null) {
            this.pop()
            return res
        }
    }

    return ChemicalElement.bySymbol[a]
}

fun StringScanner.peekChemicalElement() = this.bind().popChemicalElement()

fun StringScanner.ifNotEof(vararg actions: (StringScanner) -> Unit) {
    for(action in actions) {
        if(this.eof) {
            return
        }

        action(this)
    }
}
