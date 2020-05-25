package org.eln2.parsers

import java.io.File
import org.eln2.parsers.Gnuplot.Companion.export
import org.eln2.parsers.Gnuplot.Companion.import
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PlotTest {

    @Test
    fun testImportAndExport() {
        val dataString = File("testdata/main_2.dat").readText()
        val importedData = import(dataString)
        val exportedString = export(importedData)
        val reimportedData = import(exportedString)

        Assertions.assertEquals(importedData.size, reimportedData.size)

        importedData.zip(reimportedData).forEach { innerList ->
            Assertions.assertEquals(innerList.first.size, innerList.second.size)
            innerList.first.zip(innerList.second).forEach {
                Assertions.assertEquals(it.first, it.second)
            }
        }
    }
}
