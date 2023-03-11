package org.eln2.mc.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector4f
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
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

    GuiComponent.fill(poseStack,
        rectangle.left,
        rectangle.top,
        rectangle.right,
        rectangle.bottom,
        color.value)
}

fun renderColored(poseStack: PoseStack, color: Vector4f, rectangle: Rectangle4I) {
    renderColored(poseStack, mcColor(color), rectangle)
}

