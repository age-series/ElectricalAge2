package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import org.eln2.mc.Eln2
import org.eln2.mc.utility.Stopwatch
import java.util.*

/**
 * The Cell Graph Manager tracks the cell graphs for a single dimension.
 * It delegates updates to the graphs.
 * This is a **server-only** construct. Simulations never have to occur on the client.
 * */
class CellGraphManager(val level: Level) : SavedData() {
    private val graphs = HashMap<UUID, CellGraph>()

    private val statisticsWatch = Stopwatch()

    fun sampleTickRate(): Double {
        val elapsedSeconds = statisticsWatch.sample()

        return graphs.values.sumOf { it.sampleElapsedUpdates() } / elapsedSeconds
    }

    val totalSpentTime get() = graphs.values.sumOf { it.lastTickTime }

    /**
     * Checks whether this manager is tracking the specified graph.
     * @return True, if the graph is being tracked by this manager. Otherwise, false.
     * */
    fun contains(id: UUID): Boolean {
        return graphs.containsKey(id)
    }

    /**
     * Begins tracking a graph, and invalidates the saved data.
     * */
    fun addGraph(graph: CellGraph) {
        graphs[graph.id] = graph
        setDirty()
    }

    /**
     * Creates a fresh graph, starts tracking it, and invalidates the saved data.
     * */
    fun createGraph(): CellGraph {
        val graph = CellGraph(UUID.randomUUID(), this)
        addGraph(graph)

        return graph
    }

    /**
     * Removes a graph, and invalidates the saved data.
     * **This does not call any _destroy_ methods on the graph!**
     * */
    fun removeGraph(graph: CellGraph) {
        graphs.remove(graph.id)
        Eln2.LOGGER.info("Removed graph ${graph.id}!")
        setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val graphListTag = ListTag()

        graphs.values.forEach { graph ->
            graphListTag.add(graph.toNbt())
        }

        tag.put("Graphs", graphListTag)
        Eln2.LOGGER.info("Wrote ${graphs.count()} graphs to disk.")
        return tag
    }

    fun getGraphWithId(id: UUID): CellGraph {
        return graphs[id] ?: throw IndexOutOfBoundsException("Graph ID was not found in the cell graph ${graphs}: $id")
    }

    companion object {
        private fun load(tag: CompoundTag, level: ServerLevel): CellGraphManager {
            val manager = CellGraphManager(level)

            val graphListTag = tag.get("Graphs") as ListTag?

            if (graphListTag == null) {
                Eln2.LOGGER.info("No nodes to be loaded!")
                return manager
            }

            graphListTag.forEach { circuitNbt ->
                val graphCompound = circuitNbt as CompoundTag
                val graph = CellGraph.fromNbt(graphCompound, manager)
                if (graph.cells.isEmpty()) {
                    Eln2.LOGGER.error("Loaded circuit with no cells!")
                    return@forEach
                }

                manager.addGraph(graph)
                Eln2.LOGGER.info("Loaded ${graph.cells.count()} cells for ${graph.id}!")

                graph.buildSolver()
                graph.startSimulation()
            }

            return manager
        }

        /**
         * Gets or creates a graph manager for the specified level.
         * */
        fun getFor(level: ServerLevel): CellGraphManager {
            return level.dataStorage.computeIfAbsent({ load(it, level) }, { CellGraphManager(level) }, "CellManager")
        }
    }
}
