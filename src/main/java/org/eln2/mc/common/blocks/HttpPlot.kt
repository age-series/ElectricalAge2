package org.eln2.mc.common.blocks

import com.google.gson.Gson
import net.minecraft.core.Direction
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cell.CellGraph
import org.eln2.mc.common.cell.CellRegistry
import kotlin.concurrent.thread

object HttpPlot {
    var attempts = 0
    const val ATTEMPT_COUNT = 3

    fun connectAndSend(circuit : CellGraph) {
        if (attempts >= ATTEMPT_COUNT) {return} // Exit quickly if we've exceeded global attempt count.
        val str = Gson().toJson(JsonFrame(circuit.cells.map {
            JsonCell(it.pos.x, it.pos.z, emptyList(),  CellRegistry.registry.getValue(it.id)!!.symbol)
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
                attempts = 0 // reset this since there may have just been an issue with sending data the first time
            }
            catch(ex : Exception) {
                LOGGER.warn("Was unable to send data to the test harness.")
                // ex.printStackTrace()
                attempts += 1 // we do 3 attempts
                if (attempts >= ATTEMPT_COUNT) {
                    LOGGER.warn("We are unable to send data to the test harness, and exceeded the max attempt count.")
                }
                return@thread
            }
        }
    }

    private class JsonCell(val x : Int, val y : Int, val connections : List<Int>, val symbol : Char)
    private class JsonFrame(val cells : List<JsonCell>)
}
