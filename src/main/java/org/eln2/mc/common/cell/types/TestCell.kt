package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.apache.logging.log4j.LogManager
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellGraph
import org.eln2.mc.common.cell.CellProvider

class TestCell(pos : BlockPos) : CellBase(pos) {
    class Provider : CellProvider() {
        override fun create(pos: BlockPos): CellBase{
            return TestCell(pos)
        }

        override fun connectionPredicate(dir: Direction): Boolean {
            LogManager.getLogger().info("Predicate: $dir")
            return true
        }
    }

    private var _connections : ArrayList<CellBase>? = null
    val connections get() = _connections!!

    private var _graph : CellGraph? = null
    val graph get() = _graph!!

    private val _logger = LogManager.getLogger()

    override fun completeDiskLoad() {
        _logger.info("Cell $pos completed loading from disk!")
    }

    override fun setPlaced(currentConnections: ArrayList<CellBase>) {
        _logger.info("Set Placed By! connection count: ${currentConnections.count()}")
        _connections = currentConnections
    }

    override fun setConnections(connections: ArrayList<CellBase>) {
        _logger.info("Set Connections! connection count: ${connections.count()}")
        _connections = connections
    }

    override fun setGraph(newGraph: CellGraph) {
        _logger.info("Set new graph! ${newGraph.id}")
        _graph = newGraph
    }

    override fun setGraphAndConnections(newGraph: CellGraph, connections: ArrayList<CellBase>) {
        _logger.info("Set Graph and Connections! g: ${newGraph.id}, connection count: ${connections.count()}")
        _graph = newGraph
        _connections = connections
    }

    override fun destroy() {
        _logger.info("Destroy at $pos!")
    }

    override fun getCurrentConnections(): ArrayList<CellBase> {
        return connections
    }

    override fun getCurrentGraph(): CellGraph {
        return graph
    }
}
