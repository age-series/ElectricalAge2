package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.extensions.plus

object CellScanner {
    fun planarScan(level: Level, pos: BlockPos, searchDirection: Direction, cellFace: Direction, consumer: ICellScanConsumer){
        val remoteContainer = level
            .getBlockEntity(pos + searchDirection)
            as? ICellContainer
            ?: return

        val remoteConnectionFace = searchDirection.opposite

        val remoteSpace = remoteContainer
            .query(CellQuery(remoteConnectionFace, cellFace))
            ?: return

        if(remoteSpace.innerFace != cellFace){
            // Was causing issues with machines

            return
        }

        val remoteRelative = remoteContainer
            .probeConnectionCandidate(remoteSpace, remoteConnectionFace, ConnectionMode.Planar)
            ?: return

        consumer.consume(remoteSpace, remoteContainer, remoteRelative)
    }

    fun wrappedScan(level: Level, pos: BlockPos, searchDirection: Direction, cellFace: Direction, consumer: ICellScanConsumer){
        val wrapDirection = cellFace.opposite

        val remoteContainer = level
            .getBlockEntity(pos + searchDirection + wrapDirection)
            as? ICellContainer
            ?: return

        val remoteSpace = remoteContainer
            .query(CellQuery(cellFace, searchDirection))
            ?: return

        val remoteRelative = remoteContainer
            .probeConnectionCandidate(remoteSpace, wrapDirection.opposite, ConnectionMode.Wrapped)
            ?: return

        consumer.consume(remoteSpace, remoteContainer, remoteRelative)
    }
}
