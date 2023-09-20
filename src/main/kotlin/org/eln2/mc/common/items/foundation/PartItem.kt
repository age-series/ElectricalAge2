package org.eln2.mc.common.items.foundation

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.UseOnContext
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.parts.foundation.PartProvider
import org.eln2.mc.plus

/**
 * The Part Item delegates the placement of a Part to the Multipart Container.
 * */
class PartItem(private val provider: PartProvider) : BlockItem(
    BlockRegistry.MULTIPART_BLOCK.block.get(),
    Properties()
) {

    override fun useOn(pContext: UseOnContext): InteractionResult {
        if (pContext.level.isClientSide) {
            return InteractionResult.FAIL
        }

        if (pContext.player == null) {
            org.eln2.mc.LOG.error("Null player!")
            return InteractionResult.FAIL
        }

        val level = pContext.level as ServerLevel

        val targetPos = pContext.clickedPos + pContext.clickedFace

        org.eln2.mc.LOG.info("Interacting with multipart at $targetPos")

        var entity = level.getBlockEntity(targetPos)

        if (entity != null) {
            org.eln2.mc.LOG.info("Existing entity: $entity")

            if (entity !is MultipartBlockEntity) {
                org.eln2.mc.LOG.error("Non-multipart entity found!")

                return InteractionResult.FAIL
            }
        } else {
            org.eln2.mc.LOG.error("Placing new multipart.")

            // Place multipart
            super.useOn(pContext)

            entity = level.getBlockEntity(targetPos)
        }

        org.eln2.mc.LOG.info("Target multipart entity: $entity")

        if (entity == null) {
            return InteractionResult.FAIL
        }

        val placed = (entity as MultipartBlockEntity).place(
            pContext.player!!,
            targetPos,
            pContext.clickedFace,
            provider,
            pContext.itemInHand.tag
        )

        // If the part was placed successfully, let us consume this item.

        return if (placed) InteractionResult.SUCCESS
        else InteractionResult.FAIL
    }

    override fun getDescriptionId(): String {
        // By default, this uses the block's description ID.
        // This is not what we want.

        return orCreateDescriptionId
    }

// todo

    /*    override fun fillItemCategory(pGroup: CreativeModeTab, pItems: NonNullList<ItemStack>) {
            if (allowdedIn(pGroup)) {
                pItems.add(ItemStack(this))
            }
        }*/
}
