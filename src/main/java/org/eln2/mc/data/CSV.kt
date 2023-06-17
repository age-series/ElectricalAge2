package org.eln2.mc.data

import org.eln2.mc.mathematics.ArrayKDVectorD

data class CsvNumeric(val headers: ArrayList<String>, val entries: ArrayList<ArrayKDVectorD>)

object CsvLoader {
    fun loadNumericData(csv: String): CsvNumeric {
        val lines = csv.lines()

        val headers = ArrayList<String>()

        lines[0].split(',').forEach { headers.add(it) }

        val results = ArrayList<ArrayKDVectorD>()

        for (i in 1 until lines.size) {
            val line = lines[i]

            if(line.isEmpty()){
                continue
            }

            val tokens = line.split(',').map { it.toDoubleOrNull() ?: error("Could not parse double $it") }

            if(tokens.size != headers.size){
                error("Mismatched CSV token count")
            }

            results.add(ArrayKDVectorD(tokens.toDoubleArray()))
        }

        return CsvNumeric(headers, results)
    }
}
