@file:Suppress("NonJavaMixin", "UNCHECKED_CAST") // Applicable to static things (which we are not modifying)

package org.eln2.mc.mixin

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.ChunkBufferBuilderPack
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk
import net.minecraft.client.renderer.chunk.RenderChunkRegion
import net.minecraft.client.renderer.chunk.VisGraph
import net.minecraft.core.BlockPos
import org.eln2.mc.common.content.GridRenderer
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import org.spongepowered.asm.mixin.injection.callback.LocalCapture


@Mixin(targets = ["net.minecraft.client.renderer.chunk.ChunkRenderDispatcher\$RenderChunk\$RebuildTask"])
abstract class MixinRebuildTask {
    @Shadow(aliases = ["this$1", "f_112859_"]) // https://mcp.thiakil.com/#/class/net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask
    @Final
    private val `this$1`: RenderChunk? = null

    @Inject(
        method = ["compile"], at = [
            At(
                value = "INVOKE",
                target = "Ljava/util/Set;iterator()Ljava/util/Iterator;",
                remap = false
            )
        ],
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    fun submitGrids(
        pX: Float,
        pY: Float,
        pZ: Float,
        pChunkBufferBuilderPack: ChunkBufferBuilderPack,
        cir: CallbackInfoReturnable<*>?,
        pBuildResults : net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk.RebuildTask.CompileResults,
        pI: Int,
        pChunkMin: BlockPos,
        pChunkMax: BlockPos,
        pVisGraph: VisGraph,
        pRenderChunkRegion: RenderChunkRegion,
        pPoseStack: PoseStack,
        pRenderTypeSet: MutableSet<*>,
    ) {
        GridRenderer.submitForRenderSection(
            `this$1`!!,
            pChunkBufferBuilderPack,
            pRenderChunkRegion,
            pRenderTypeSet as MutableSet<RenderType>
        )
    }
}
