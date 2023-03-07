package org.eln2.mc.utility

import org.eln2.mc.mathematics.KDVectorD


data class CsvNumeric(val headers: ArrayList<String>, val entries: ArrayList<KDVectorD>)

object CsvLoader {
    fun loadNumericData(csv: String): CsvNumeric {
        val lines = csv.lines()

        val headers = ArrayList<String>()

        lines[0].split(',').forEach { headers.add(it) }

        val results = ArrayList<KDVectorD>()

        for (i in 1 until lines.size) {
            val line = lines[i]

            if(line.isEmpty()){
                continue
            }

            val tokens = line.split(',').map { it.toDouble() }

            if(tokens.size != headers.size){
                error("Mismatched CSV token count")
            }

            results.add(KDVectorD(tokens.toDoubleArray()))
        }

        return CsvNumeric(headers, results)
    }
}
