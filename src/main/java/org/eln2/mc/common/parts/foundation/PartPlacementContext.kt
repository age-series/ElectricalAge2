package org.eln2.mc.common.parts.foundation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.space.*

/**
 * Encapsulates all the data associated with a part's placement.
 * */
data class PartPlacementContext(
    /**
     * The final position of the part. It is one block along the normal, starting from the clicked block.
     * */
    val pos: BlockPos,

    /**
     * The clicked face of the block.
     * */
    val face: Direction,
    val horizontalFacing: Direction,
    val level: Level,
    val multipart: MultipartBlockEntity
) {
    fun createDescriptor(): LocationDescriptor {
        return LocationDescriptor()
            .withLocator(BlockPosLocator(pos))
            .withLocator(IdentityDirectionLocator(horizontalFacing)) // is this right?
            .withLocator(BlockFaceLocator(face))
    }
}
