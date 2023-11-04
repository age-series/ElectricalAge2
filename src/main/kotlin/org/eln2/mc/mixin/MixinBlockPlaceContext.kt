@file:Suppress("NonJavaMixin", "CAST_NEVER_SUCCEEDS") // Applicable to static things (which we are not modifying)

package org.eln2.mc.mixin

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import org.eln2.mc.common.content.GridConnectionManagerClient
import org.eln2.mc.common.content.GridConnectionManagerServer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(BlockPlaceContext::class)
class MixinBlockPlaceContext {
    @Inject(
        at = [At("HEAD")],
        method = ["Lnet/minecraft/world/item/context/BlockPlaceContext;canPlace()Z"],
        cancellable = true
    )
    fun checkCanPlaceClipsGrid(cir: CallbackInfoReturnable<Boolean>) {
        this as BlockPlaceContext

        val level = this.level

        if(level == null) {
            println("LEVEL NIL")
            return
        }

        if(level.isClientSide) {
            if(GridConnectionManagerClient.clipsBlock(clickedPos)) {
                cir.returnValue = false
            }
        }
        else {
            if(GridConnectionManagerServer.clipsBlock(level as ServerLevel, clickedPos)) {
                cir.returnValue = false
            }
        }
    }
}
