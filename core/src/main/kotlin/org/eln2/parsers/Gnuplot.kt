package org.eln2.parsers

/**
Gnuplot Parser Class

Congratulations, this is either a TDV or a SDV (tab delimited vales or space delimited values) file :D
 */
class Gnuplot {
    companion object {

        /**
         * import: imports a gnuplot plot string into a doubly linked list.
         *
         * NOTE: Does not check for the length of individual axis data
         *
         * @param data: Gnuplot plot string
         * @return Doubly Linked List with Doubles
         */
        fun import(data: String): ArrayList<ArrayList<Double>> {
            val list = ArrayList<ArrayList<Double>>()
            for (line in data.split("\n")) {
                if (line.trim().isNotEmpty() && line.trim()[0] != '#') {
                    // If you get this far, it's not a comment and it's not an empty line. There exists DATA!
                    val innerList = ArrayList<Double>()
                    // We allow for tabs or spaces to be used as delimiters because the documentation says "whitespace"
                    // I presume that means not newlines, since they use those to separate rows of data.
                    for (field in line.split(" ", "\t")) {
                        val dbl = field.trim().toDoubleOrNull()
                        if (dbl != null) {
                            innerList.add(dbl)
                        }
                    }
                    list.add(innerList)
                }
            }
            return list
        }

        /**
         * export: exports a doubly linked list of Doubles into a gnuplot plot string
         *
         * NOTE: Does not check for the length of individual axis data
         *
         * @param data: Doubly linked list of doubles
         * @param header: Header string to put at the top of your file (for example, axis plot names)
         * @param separator: Separates out the data on lines. Should be space or tab character.
         * @return Gnuplot plot string
         */
        fun export(data: ArrayList<ArrayList<Double>>, header: String = "", separator: Char = ' '): String {
            var output = header
            output += data.map { row ->
                "${
                row.map { field -> "$field$separator" }.fold("", { acc, it -> acc + it }).trim()
                }\n"
            }.fold("", { acc, it -> acc + it })
            return output
        }
    }
}
