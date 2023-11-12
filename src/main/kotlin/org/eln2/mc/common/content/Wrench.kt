package org.eln2.mc.common.content

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import org.eln2.mc.LOG
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity

interface WrenchRotatablePart {
    @ServerOnly
    fun canRotateWithWrench(wrench: WrenchItem, context: UseOnContext): Boolean = true
}

interface WrenchInteractablePart {
    @ServerOnly
    fun applyWrench(wrench: WrenchItem, context: UseOnContext) : InteractionResult
}

class WrenchItem : Item(Properties()) {
    override fun useOn(pContext: UseOnContext): InteractionResult {
        if(pContext.level.isClientSide) {
            return InteractionResult.PASS
        }

        val player = pContext.player ?: return InteractionResult.FAIL

        val multipart = pContext.level.getBlockEntity(pContext.clickedPos) as? MultipartBlockEntity ?: return InteractionResult.FAIL

        val part = multipart.pickPart(player) ?: return InteractionResult.FAIL

        if(part is WrenchInteractablePart) {
            if(player.isShiftKeyDown) {
                return part.applyWrench(this, pContext)
            }
        }

        if(part !is WrenchRotatablePart || !part.canRotateWithWrench(this, pContext)) {
            return InteractionResult.FAIL
        }

        val tag = CompoundTag()

        multipart.breakPart(part, tag)

        val orientation = if(player.isShiftKeyDown) {
            part.placement.horizontalFacing.clockWise
        }
        else {
            part.placement.horizontalFacing.counterClockWise
        }

        val flag = multipart.place(
            player,
            part.placement.position,
            part.placement.face,
            part.placement.provider,
            saveTag = tag,
            orientation = orientation
        )

        if(!flag) {
            LOG.error("FAILED TO PLACE PART WITH WRENCH! $player ${part.placement.position} ${part.placement.face} ${part.placement.provider} $tag $orientation $part ${part.placement.provider}")
        }

        return InteractionResult.SUCCESS
    }
}
