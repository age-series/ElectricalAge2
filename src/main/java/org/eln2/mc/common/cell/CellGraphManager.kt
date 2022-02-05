package org.eln2.mc.common.cell

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import org.apache.logging.log4j.LogManager
import org.eln2.mc.common.In
import org.eln2.mc.common.Side
import java.util.*

@In(Side.LogicalServer)
class CellGraphManager(val level : Level) : SavedData() {
    private val _logger = LogManager.getLogger()
    private val _graphs = HashMap<UUID, CellGraph>()

    fun containsGraph(graph : CellGraph) : Boolean{
        return _graphs.containsKey(graph.id)
    }

    fun containsGraphWithId(id : UUID) : Boolean{
        return _graphs.containsKey(id)
    }

    fun addGraph(graph : CellGraph) {
        _graphs[graph.id] = graph
        _logger.info("Added graph ${graph.id}!")
        setDirty()
    }

    fun removeGraph(graph : CellGraph) {
        _graphs.remove(graph.id)
        _logger.info("Removed graph ${graph.id}!")
        setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val graphListTag = ListTag()

        _graphs.values.forEach { graph->
            graphListTag.add(graph.serializeNbt())
        }

        tag.put("Graphs", graphListTag)

        _logger.info("Wrote ${_graphs.count()} graphs to disk.")

        return tag
    }

    fun getGraphWithId(id : UUID) : CellGraph{
        return _graphs[id]!!
    }

    companion object {
        private val LOGGER = LogManager.getLogger()

        private fun load(tag : CompoundTag, level : ServerLevel) : CellGraphManager {
            val manager = CellGraphManager(level)

            val graphListTag = tag.get("Graphs") as ListTag?

            if(graphListTag == null){
                LOGGER.info("No nodes to be loaded!")
                return manager
            }

            graphListTag.forEach { circuitNbt ->
                val graphCompound  = circuitNbt as CompoundTag
                val graph = CellGraph.deserializeNbt(graphCompound, manager)

                if(graph.cells.isEmpty()){
                   LOGGER.error("Loaded circuit with no cells!")
                    return@forEach
                }

                manager.addGraph(graph)
                LOGGER.info("Loaded ${graph.cells.count()} cells for ${graph.id}!")
            }

            return manager
        }

        fun getFor(level : ServerLevel) : CellGraphManager {
            val storage = level.dataStorage

            // this will return the instance or create a new one
            return storage.computeIfAbsent({ load(it, level) }, { CellGraphManager(level) }, "CellManager")
        }
    }
}
