package org.eln2.mc.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector4f
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.mathematics.Vector2F
import org.eln2.mc.mathematics.Vector2I

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
