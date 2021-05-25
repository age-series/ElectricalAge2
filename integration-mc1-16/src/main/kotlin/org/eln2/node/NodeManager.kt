package org.eln2.node

import net.minecraft.nbt.CompoundNBT
import net.minecraft.nbt.CompressedStreamTools
import org.eln2.Eln2
import org.eln2.utils.Coordinate
import java.nio.file.Path

object NodeManager {
    var path: Path? = null

    private var nbtData: CompoundNBT = CompoundNBT()

    init {
        readNodes()
    }

    fun readNodes() {
        val localPath = path
        nbtData = if (localPath != null && localPath.toFile().exists()) {
            CompressedStreamTools.readCompressed(localPath.toFile())
        } else {
            CompoundNBT()
        }
    }

    fun writeNodes() {
        nodeList.forEach {
            nbtData.putString(it.key.toString(), "${it.value.registryName?.namespace}_${it.value.registryName?.path}")
        }
        val localPath = path
        if (localPath != null) {
            CompressedStreamTools.writeCompressed(nbtData, localPath.toFile())
        }
    }

    private val simulationList = mutableListOf<ISim>()
    private val nodeList = mutableMapOf<Coordinate,NodeBlock>()

    fun addNode(coord: Coordinate, block: NodeBlock) {
        // TODO: Get world file and verify that these nodes are actually ours
        nodeList[coord] = block
        Eln2.LOGGER.debug("Dim info2: ${block.coordinate?.world?.dimensionKey?.location}")
        writeNodes()
    }

    fun removeNode(coord: Coordinate) {
        // TODO: Get world file and verify that these nodes are actually ours
        nodeList.remove(coord)
        writeNodes()
    }
}
