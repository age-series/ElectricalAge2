package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Multipart entities
 *  - Are "hollow" entities, that do not have any special data or logic by themselves
 *  - Act as a container for Parts. There may be one part per inner face (maximum of 6 parts per multipart entity)
 *  - The player can place inside the multipart entity. Placement and breaking logic must be emulated.
 *  - The multipart entity saves data for all the parts. Parts are responsible for their rendering.
 * */
class MultipartBlockEntity (var pos : BlockPos, var state: BlockState): BlockEntity(BlockRegistry.MULTIPART_BLOCK_ENTITY.get(), pos, state) {
    val parts = HashMap<Direction, Part>()

    fun tryPlacePart(){

    }

}
