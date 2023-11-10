@file:Suppress("NonJavaMixin")
@file:JvmName("MixinRenderRegionCache")
@file:Mixin(RenderRegionCache::class)

package org.eln2.mc.mixin

import net.minecraft.client.renderer.chunk.RenderRegionCache
import net.minecraft.core.BlockPos
import org.eln2.mc.common.content.GridConnectionManagerClient
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Inject(
    at = [At("HEAD")],
    method = ["Lnet/minecraft/client/renderer/chunk/RenderRegionCache;isAllEmpty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;II[[Lnet/minecraft/client/renderer/chunk/RenderRegionCache\$ChunkInfo;)Z"],
    cancellable = true
)
private fun flagGridSectionsAsNotEmpty(pStart: BlockPos, pEnd: BlockPos, p_200473_: Int, p_200474_: Int, pInfos: Array<Array<RenderRegionCache.ChunkInfo>>, cir: CallbackInfoReturnable<Boolean>) {
    if(GridConnectionManagerClient.containsRange(pStart, pEnd)) {
        cir.returnValue = false
    }
}
