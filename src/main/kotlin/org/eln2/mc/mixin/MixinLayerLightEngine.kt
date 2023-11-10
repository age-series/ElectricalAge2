@file:Suppress("NonJavaMixin") // Applicable to static things (which we are not modifying)

package org.eln2.mc.mixin

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.chunk.LightChunkGetter
import net.minecraft.world.level.lighting.LayerLightEngine
import net.minecraftforge.server.ServerLifecycleHooks
import org.eln2.mc.common.blocks.foundation.GhostLightHackClient
import org.eln2.mc.common.blocks.foundation.GhostLightServer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import kotlin.math.max

@Mixin(LayerLightEngine::class)
abstract class MixinLayerLightEngine  {
    @Shadow
    private val layer: LightLayer? = null

    @Shadow
    private val chunkSource: LightChunkGetter? = null

    private fun getOverride(pBlockPos: BlockPos): Int {
        if(layer != LightLayer.BLOCK) {
            return 0
        }

        val level = chunkSource?.level as? Level

        if(level != null) {
            if(level.isClientSide) {
                return GhostLightHackClient.getBlockBrightness(pBlockPos)
            }
            else if(level is ServerLevel) {
                val server = ServerLifecycleHooks.getCurrentServer()

                if(server != null && server.isSameThread) {
                    return GhostLightServer.getBlockBrightness(level, pBlockPos)
                }
            }
        }

        return 0
    }

    @Inject(
        at = [At("RETURN")],
        method = ["Lnet/minecraft/world/level/lighting/LayerLightEngine;getLightValue(Lnet/minecraft/core/BlockPos;)I"],
        cancellable = true
    )
    private fun getLightValue(pBlockPos: BlockPos, callback: CallbackInfoReturnable<Int>) {
        callback.returnValue = max(callback.returnValue, getOverride(pBlockPos))
    }
}
