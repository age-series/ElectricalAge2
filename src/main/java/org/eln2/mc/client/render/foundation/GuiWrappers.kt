package org.eln2.mc.client.render.foundation

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Matrix4f
import com.mojang.math.Vector4f
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.mathematics.Rectangle4F
import org.eln2.mc.mathematics.Rectangle4I
import org.eln2.mc.mathematics.Vector2F
import org.eln2.mc.mathematics.Vector2I
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.mcColor

fun renderTextured(
    texture: ResourceLocation,
    poseStack: PoseStack,
    blitOffset: Int = 0,
    color: Vector4f,
    position: Vector2I,
    uvSize: Vector2I,
    uvPosition: Vector2F,
    textureSize: Vector2I) {

    RenderSystem.setShader { GameRenderer.getPositionTexShader() }
    RenderSystem.setShaderColor(color.x(), color.y(), color.z(), color.w())
    RenderSystem.setShaderTexture(0, texture)

    GuiComponent.blit(
        poseStack,
        position.x,
        position.y,
        blitOffset,
        uvPosition.x,
        uvPosition.y,
        uvSize.x,
        uvSize.y,
        textureSize.x,
        textureSize.y)
}

fun renderColored(poseStack: PoseStack, color: McColor, rectangle: Rectangle4I) {
    RenderSystem.setShader { GameRenderer.getPositionColorShader() }

    GuiComponent.fill(
        poseStack,
        rectangle.left,
        rectangle.top,
        rectangle.right,
        rectangle.bottom,
        color.value)
}

fun renderColored(poseStack: PoseStack, color: Vector4f, rectangle: Rectangle4I) {
    renderColored(poseStack, mcColor(color), rectangle)
}

fun renderColored(poseStack: PoseStack, color: McColor, rectangle: Rectangle4F) {
    RenderSystem.setShader { GameRenderer.getPositionColorShader() }

    fill(
        poseStack,
        rectangle.left,
        rectangle.top,
        rectangle.right,
        rectangle.bottom,
        color.value)
}

fun renderColored(poseStack: PoseStack, color: Vector4f, rectangle: Rectangle4F) {
    renderColored(poseStack, mcColor(color), rectangle)
}

private fun fill(pPoseStack: PoseStack, pMinX: Float, pMinY: Float, pMaxX: Float, pMaxY: Float, pColor: Int) {
    innerFill(pPoseStack.last().pose(), pMinX, pMinY, pMaxX, pMaxY, pColor)
}
private fun innerFill(
    pMatrix: Matrix4f,
    pMinX: Float,
    pMinY: Float,
    pMaxX: Float,
    pMaxY: Float,
    pColor: Int) {

    var minX = pMinX
    var minY = pMinY
    var maxX = pMaxX
    var maxY = pMaxY
    if (minX < maxX) {
        val i = minX
        minX = maxX
        maxX = i
    }
    if (minY < maxY) {
        val j = minY
        minY = maxY
        maxY = j
    }
    val f3 = (pColor shr 24 and 255).toFloat() / 255.0f
    val f = (pColor shr 16 and 255).toFloat() / 255.0f
    val f1 = (pColor shr 8 and 255).toFloat() / 255.0f
    val f2 = (pColor and 255).toFloat() / 255.0f
    val bufferBuilder = Tesselator.getInstance().builder
    RenderSystem.enableBlend()
    RenderSystem.disableTexture()
    RenderSystem.defaultBlendFunc()
    RenderSystem.setShader { GameRenderer.getPositionColorShader() }
    bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
    bufferBuilder.vertex(pMatrix, minX, maxY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.vertex(pMatrix, maxX, maxY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.vertex(pMatrix, maxX, minY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.vertex(pMatrix, minX, minY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.end()
    BufferUploader.end(bufferBuilder)
    RenderSystem.enableTexture()
    RenderSystem.disableBlend()
}
