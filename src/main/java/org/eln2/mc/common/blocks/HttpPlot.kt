package org.eln2.mc.common.blocks

import com.google.gson.Gson
import net.minecraft.core.Direction
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.client.overlay.plotter.PlotterOverlay
import org.eln2.mc.common.cell.CellGraph
import org.eln2.mc.common.cell.CellRegistry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

object HttpPlot {
    var allowed = AtomicBoolean(true)

    fun connectAndSend(circuit : CellGraph) {
        if(circuit.cells.count() > 1){
            PlotterOverlay.latestGraph = circuit
        }

        if (!allowed.get()) {
            return
        }

        val str = Gson().toJson(JsonFrame(circuit.cells.map {
            JsonCell(it.pos.x, it.pos.z, emptyList(),  CellRegistry.registry.getValue(it.id)!!.symbol, "n/a")
        }))

        thread(start = true){
            val serverURL = "http://127.0.0.1:3141/"
            val post = HttpPost(serverURL)
            post.entity = StringEntity(str)
            val client = HttpClients.createDefault()
            try {
                val response = client.execute(post)
                response.close()
                client.close()
            }
            catch(ex : Exception) {
                LOGGER.warn("Was unable to send data to the test harness.")
                // ex.printStackTrace()
                allowed.set(false)
                Thread.sleep(1000)
                allowed.set(true)
                return@thread
            }
        }
    }

    private class JsonCell(val x : Int, val y : Int, val connections : List<Int>, val symbol : Char, val info : String)
    private class JsonFrame(val cells : List<JsonCell>)
}
