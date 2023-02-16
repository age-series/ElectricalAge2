package org.eln2.mc.extensions

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.renderer.GameRenderer

object PoseStackExtensions {
    // todo: remove this nonsense

    class Union4i(val a: Int, val b: Int, val c: Int, val d: Int)

    fun PoseStack.blitMultiple(collection: ArrayList<Union4i>) {
        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder

        val matrix = this.last().pose()

        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        collection.forEach { union4i ->
            val pX1 = union4i.a.toFloat()
            val pY1 = union4i.b.toFloat()
            val width = union4i.c.toFloat()
            val height = union4i.d.toFloat()

            val pX2 = pX1 + width
            val pY2 = pY1 + height

            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            builder.vertex(matrix, pX1, pY2, 0f).uv(0f, height).endVertex()
            builder.vertex(matrix, pX2, pY2, 0f).uv(width, height).endVertex()
            builder.vertex(matrix, pX2, pY1, 0f).uv(width, 0f).endVertex()
            builder.vertex(matrix, pX1, pY1, 0f).uv(0f, 0f).endVertex()
            builder.end()
        }

        BufferUploader.end(builder)
    }
}

