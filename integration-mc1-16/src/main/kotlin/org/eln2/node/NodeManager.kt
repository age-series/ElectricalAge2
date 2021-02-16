package org.eln2.node

import net.minecraft.nbt.CompoundNBT
import net.minecraft.world.storage.WorldSavedData
import org.eln2.utils.Coordinate

object NodeManager: WorldSavedData("eln2") {

    override fun isDirty() = true

    private val nodeList = mutableMapOf<Coordinate,NodeBlock>()

    fun addNode(coord: Coordinate, block: NodeBlock) {
        nodeList[coord] = block
        println("Dim info2: ${block.coordinate?.world?.dimensionKey?.location}")
    }

    fun removeNode(coord: Coordinate) {
        nodeList.remove(coord)
    }

    override fun read(p0: CompoundNBT) {
        println("The contents of P0: $p0")
        nodeList.forEach {
            it.value.read(p0)
        }
    }

    override fun write(p0: CompoundNBT): CompoundNBT {
        nodeList.forEach {
            it.value.write(p0)
        }
        println("Writing P0: $p0")
        return p0
    }
}
