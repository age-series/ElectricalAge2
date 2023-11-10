@file:Suppress("NonJavaMixin")
@file:JvmName("MixinRenderType")
@file:Mixin(RenderType::class) // frak this?

package org.eln2.mc.mixin

import net.minecraft.client.renderer.RenderType
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Inject(
    at = [At("RETURN")],
    method = ["Lnet/minecraft/client/renderer/RenderType;chunkBufferLayers()Ljava/util/List;"],
    cancellable = true
) // Maybe strip down using access transformers and just change the field instead of this?
private fun getChunkRenderLayers(cir: CallbackInfoReturnable<List<RenderType>>) {
   // todo
}
