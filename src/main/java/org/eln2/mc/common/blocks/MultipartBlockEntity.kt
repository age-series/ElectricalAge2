package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import org.eln2.mc.Eln2
import org.eln2.mc.common.parts.Part
import org.eln2.mc.common.parts.PartProvider
import org.eln2.mc.extensions.AABBExtensions.viewClip
import org.eln2.mc.extensions.Vec3Extensions.minus

/**
 * Multipart entities
 *  - Are dummy entities, that do not have any special data or logic by themselves
 *  - Act as a container for Parts. There may be one part per inner face (maximum of 6 parts per multipart entity)
 *  - The player can place inside the multipart entity. Placement and breaking logic must be emulated.
 *  - The multipart entity saves data for all the parts. Parts are responsible for their rendering.
 * */
class MultipartBlockEntity (var pos : BlockPos, var state: BlockState) :
    BlockEntity(BlockRegistry.MULTIPART_BLOCK_ENTITY.get(), pos, state) {

    private val parts = HashMap<Direction, Part>()

    private val boundingBox : AABB get(){
        return AABB(pos)
    }

    init {
        Eln2.LOGGER.info("Constructed multipart block entity $this")
    }

    override fun setRemoved() {
        Eln2.LOGGER.info("Multipart block entity removed")

        super.setRemoved()

    }

    private fun pickPart(entity : Player) : Part?{
        // I don't like what I did here very much.
        // also does not work very well. todo

        parts.values
            .sortedBy { (entity.eyePosition - it.boundingBox.center).lengthSqr() }
            .forEach { part ->
                val aabb = part.boundingBox

                val intersection = aabb.viewClip(entity)

                if(!intersection.isEmpty){
                    return part
                }
            }

        return null
    }

    fun place(entity: Player, pos : BlockPos, face : Direction, provider : PartProvider) : Boolean{
        Eln2.LOGGER.info("Part placing on $face")

        if(parts.containsKey(face)){
            Eln2.LOGGER.error("Already have a part there!")
            return false
        }

        val part = provider.create(pos, face)

        assert(parts.put(face, part) == null)

        Eln2.LOGGER.info("Successfully placed part!")

        // to test picking:

        val picked = pickPart(entity)

        Eln2.LOGGER.info("Picked: $picked, expected: $part")

        return true
    }
}
