package org.eln2.mc.common.items

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.UseOnContext
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.MultipartBlockEntity
import org.eln2.mc.common.parts.PartProvider
import org.eln2.mc.extensions.BlockPosExtensions.plus

class PartItem(private val provider: PartProvider) : BlockItem(BlockRegistry.MULTIPART_BLOCK.block.get(), Properties()) {
    override fun useOn(pContext: UseOnContext): InteractionResult {
        if(pContext.level.isClientSide){
            return InteractionResult.PASS
        }

        if(pContext.player == null){
            Eln2.LOGGER.error("Null player!")
            return InteractionResult.FAIL
        }

        val level = pContext.level as ServerLevel

        val targetPos = pContext.clickedPos + pContext.clickedFace

        Eln2.LOGGER.info("Interacting with multipart at $targetPos");

        var entity = level.getBlockEntity(targetPos)

        if(entity != null){
            Eln2.LOGGER.info("Existing entity: $entity")

            if(entity !is MultipartBlockEntity){
                Eln2.LOGGER.error("Non-multipart entity found!")

                return InteractionResult.FAIL
            }
        }
        else {
            Eln2.LOGGER.error("Placing new multipart.")

            super.useOn(pContext)

            entity = level.getBlockEntity(targetPos)
        }

        Eln2.LOGGER.info("Target multipart entity: $entity")

        if(entity == null){
            return InteractionResult.FAIL
        }

        val placed = (entity as MultipartBlockEntity).place(pContext.player!!, targetPos, pContext.clickedFace, provider)

        // ignore for now

        return InteractionResult.SUCCESS;
    }
}
