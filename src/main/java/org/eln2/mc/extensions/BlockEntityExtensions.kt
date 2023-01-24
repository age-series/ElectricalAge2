package org.eln2.mc.extensions

import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity

object BlockEntityExtensions {
    inline fun <reified T> BlockEntity.getNeighborEntity(direction: Direction) : T? where T : BlockEntity{
        val neighborPosition = this.blockPos.relative(direction)
        val entity = this.level?.getBlockEntity(neighborPosition)

        return entity as? T
    }

    inline fun <reified T> BlockEntity.getNeighborEntities() : ArrayList<T> where T : BlockEntity{
        val results = ArrayList<T>()

        Direction.values().forEach { direction ->
            val entity = this.getNeighborEntity<T>(direction)

            if(entity != null){
                results.add(entity)
            }
        }

        return results
    }
}
