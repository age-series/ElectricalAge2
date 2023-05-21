package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import org.eln2.mc.common.space.*
import org.eln2.mc.extensions.plus

fun interface IScanConsumer {
    fun consume(neighborInfo: CellNeighborInfo)
}

fun planarScan(level: Level, actualCell: CellBase, searchDirection: Direction, consumer: IScanConsumer) {
    val actualPosWorld = actualCell.pos.descriptor.requireLocator<R3, BlockPosLocator> { "Planar Scan requires a block position" }.pos
    val remoteContainer = level.getBlockEntity(actualPosWorld + searchDirection) as? ICellContainer ?: return
    val connectionFaceTarget = searchDirection.opposite

    remoteContainer
        .getCells()
        .filter {
            // Select cells that we can search using this algorithm. Those cells are SE(3) parameterized, so we can search using the position and face rotation.
            val desc = it.pos.descriptor

            desc.hasLocator<R3, BlockPosLocator>() && desc.hasLocator<SO3, BlockFaceLocator>()
        }
        .forEach { targetCell ->
            val targetFaceTarget = targetCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator>().innerFace

            if(targetFaceTarget == connectionFaceTarget) {
                if(actualCell.acceptsConnection(targetCell) && targetCell.acceptsConnection(actualCell)){
                    consumer.consume(CellNeighborInfo(targetCell, remoteContainer))
                }
            }
        }
}

fun wrappedScan(level: Level, actualCell: CellBase, searchDirection: Direction, consumer: IScanConsumer){
    val actualPosWorld = actualCell.pos.descriptor.requireLocator<R3, BlockPosLocator> { "Wrapped Scan requires a block position" }.pos
    val actualFaceActual = actualCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator> { "Wrapped Scan requires a face" }.innerFace
    val wrapDirection = actualFaceActual.opposite
    val remoteContainer = level.getBlockEntity(actualPosWorld + searchDirection + wrapDirection) as? ICellContainer ?: return
    val connectDirectionTarget = wrapDirection.opposite

    remoteContainer
        .getCells()
        .filter {
            // Select cells that we can search using this algorithm. Those cells are SE(3) parameterized, so we can search using the position and face rotation.
            val desc = it.pos.descriptor
            desc.hasLocator<R3, BlockPosLocator>() && desc.hasLocator<SO3, BlockFaceLocator>()
        }
        .forEach { targetCell ->
            val targetFaceTarget = targetCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator>().innerFace

            if(targetFaceTarget == connectDirectionTarget) {
                if(actualCell.acceptsConnection(targetCell) && targetCell.acceptsConnection(actualCell)){
                    consumer.consume(CellNeighborInfo(targetCell, remoteContainer))
                }
            }
        }
}
