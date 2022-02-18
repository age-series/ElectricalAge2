package org.eln2.mc.utility

class McColoredString(val string : String, val color : McColor)

object ColoredStringFormatter {

    fun addColor(str : String, color : McColor) : String {
        return "[C${color}]$str"
    }

    // todo: parse multiple colored strings if required (implement lexer)
    fun getColors(str: String): McColoredString? {
        if (!str.startsWith('[')) {
            return null
        }

        if (str[1] != 'C') {
            throw Exception("Unknown control character: ${str[1]}")
        }

        val sb = StringBuilder()
        var index = 2

        while (true) {
            if (index > str.length - 1) {
                throw Exception("Malformed color string! \"$str\"")
            }

            val current = str[index++]

            if (current == ']') {
                break
            }

            sb.append(current)
        }

        val color = McColor.fromString(sb.toString())
        val toSkip = 3 + sb.count()

        return McColoredString(str.removeRange(0, toSkip), color)
    }
}
