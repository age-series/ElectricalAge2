package org.eln2.mc.common.blocks

import com.google.gson.Gson
import net.minecraft.core.Direction
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.eln2.mc.common.cell.CellGraph
import kotlin.concurrent.thread

object HttpPlot {
    fun connectAndSend(circuit : CellGraph) {
        val str = Gson().toJson(JsonFrame(circuit.cells.map {
            JsonCell(it.pos.x, it.pos.z, emptyList())
        }))
        val t = thread(start = true){
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
                return@thread
            }
        }
    }

    private class JsonCell(val x : Int, val y : Int, val connections : List<Int>)
    private class JsonFrame(val cells : List<JsonCell>)

    private fun prepareJsonNode(tile : CellTileEntity) : JsonCell {
        val sides = ArrayList<Int>()

        tile.getAdjacentSides().forEach {
            when(it){
                Direction.NORTH -> sides.add(0)
                Direction.SOUTH -> sides.add(1)
                Direction.EAST -> sides.add(2)
                Direction.WEST -> sides.add(3)
                else -> {throw Exception("Plotter does not support Y axis!")}
            }
        }

        return JsonCell(tile.pos.x, tile.pos.z, sides)
    }
}
