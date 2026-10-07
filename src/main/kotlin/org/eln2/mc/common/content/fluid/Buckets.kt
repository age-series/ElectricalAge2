package org.eln2.mc.common.content.fluid

import net.minecraft.world.InteractionResult
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fluids.FluidUtil

object BucketFluidInteraction {
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val itemStack = event.itemStack

        if (!itemStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent) {
            return
        }

        val level = event.level
        val pos = event.pos

        val blockEntity = level.getBlockEntity(pos)
            ?: return

        val capability = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, event.face)

        if (!capability.isPresent) {
            return
        }

        /**
         * Bucket-on-tank always means tank interaction: cancel even when the transfer will fail (full tank, incompatible fluid), so a filled ELN2 bucket never places a fluid block against a tank face.
         * Filled buckets still place fluid in the world when clicked on a non-tank block.
         * */
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS

        if (level.isClientSide) {
            return
        }

        FluidUtil.interactWithFluidHandler(event.entity, event.hand, level, pos, event.face)
    }
}
