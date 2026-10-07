@file:Suppress("unused")

package org.eln2.mc.client.dynamicLight

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.entity.BlockEntity
import org.lwjgl.opengl.GL11

object SceneAlbedoRenderer {
    private const val LIGHTMAP_SIZE = 16

    private var whiteLightmapImage: NativeImage? = null

    fun render(
        target: TextureTarget,
        view: SceneView,
        frames: List<DynamicLightFrame>,
        frustum: Frustum,
        level: ClientLevel,
        partialTick: Float,
    ) {
        RenderSystem.assertOnRenderThread()

        target.bindWrite(true)
        RenderSystem.colorMask(true, true, true, true)
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX)

        val minecraft = Minecraft.getInstance()
        val lightmapTexture = minecraft.gameRenderer.lightTexture().lightTexture
        val savedFogStart = RenderSystem.getShaderFogStart()

        val blockEntities = LinkedHashSet<BlockEntity>()
        val entities = LinkedHashSet<Entity>()

        for (frame in frames) {
            blockEntities.addAll(frame.blockEntities)

            for (entity in frame.entities) {
                if (isRenderedByLevel(entity, view.camera, frustum)) {
                    entities.add(entity)
                }
            }
        }

        uploadWhiteLightmap(lightmapTexture)
        RenderSystem.setShaderFogStart(Float.MAX_VALUE)

        try {
            prepareState(target)
            LightSceneRenderer.drawTerrain(view)

            prepareState(target)
            LightSceneRenderer.drawFlywheel(view, level, partialTick)

            prepareState(target)
            LightSceneRenderer.drawBlockEntities(view, blockEntities, level, partialTick, target, frustum)

            prepareState(target)
            LightSceneRenderer.drawEntities(view, entities, level, partialTick, target, false)
        } finally {
            RenderSystem.setShaderFogStart(savedFogStart)
            lightmapTexture.upload()
        }
    }

    private fun uploadWhiteLightmap(lightmapTexture: DynamicTexture) {
        val image = whiteLightmapImage ?: NativeImage(LIGHTMAP_SIZE, LIGHTMAP_SIZE, false).also {
            it.fillRect(0, 0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, -1)
            whiteLightmapImage = it
        }

        GlStateManager._bindTexture(lightmapTexture.id)
        image.upload(0, 0, 0, false)
    }

    private fun isRenderedByLevel(entity: Entity, camera: Camera, frustum: Frustum): Boolean {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        val cameraPosition = camera.position
        val isInView = minecraft.entityRenderDispatcher.shouldRender(
            entity,
            frustum,
            cameraPosition.x,
            cameraPosition.y,
            cameraPosition.z
        )

        if (!isInView && (player == null || !entity.hasIndirectPassenger(player))) {
            return false
        }

        val cameraEntity = camera.entity
        val isSleepingCamera = cameraEntity is LivingEntity && cameraEntity.isSleeping

        if (entity === cameraEntity && !camera.isDetached && !isSleepingCamera) {
            return false
        }

        if (entity is LocalPlayer && entity !== cameraEntity && (entity !== player || entity.isSpectator)) {
            return false
        }

        return true
    }

    private fun prepareState(target: TextureTarget) {
        target.bindWrite(true)
        RenderSystem.enableDepthTest()
        RenderSystem.depthFunc(GL11.GL_LEQUAL)
        RenderSystem.depthMask(false)
        RenderSystem.colorMask(true, true, true, true)
        RenderSystem.disableBlend()
    }
}
