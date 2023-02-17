package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import org.eln2.mc.Eln2
import org.eln2.mc.utility.AveragingList
import org.eln2.mc.utility.Time
import java.util.*
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

/**
 * The Cell Graph Manager tracks the cell graphs for a single dimension.
 * It delegates updates to the graphs.
 * This is a **server-only** construct. Simulations never have to occur on the client.
 * */
class CellGraphManager(val level: Level) : SavedData() {
    // todo: proper parallel execution, after the objects are done
    private val executor = Executors.newWorkStealingPool(12)
    private val completionService = ExecutorCompletionService<Long>(executor)
    private val averageSeconds = AveragingList(100)

    private val graphs = HashMap<UUID, CellGraph>()

    // Could also use graph count, but let's be safe.
    private var runningTasks = 0

    private var logCountdown = 100

    fun beginUpdate() {
        graphs.values.forEach { graph ->
            completionService.submit {
                graph.update()

                return@submit graph.latestSolveTime
            }

            runningTasks++
        }
    }

    fun endUpdate() {
        var totalTime = 0.0

        while (runningTasks-- > 0) {
            val nanoseconds = completionService.take().get()

            totalTime += Time.toSeconds(nanoseconds)
        }

        runningTasks = 0

        averageSeconds.addSample(totalTime)

        if (--logCountdown == 0) {
            logCountdown = 100

            Eln2.LOGGER.info("Average simulation time: ${averageSeconds.calculate() * 1000}ms")
        }
    }
    /*

        fun update(){
            graphs.values.forEach{ it.update() }
        }
    */

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
