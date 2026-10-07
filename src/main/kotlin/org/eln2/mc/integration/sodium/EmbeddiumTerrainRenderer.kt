package org.eln2.mc.integration.sodium

import com.mojang.blaze3d.vertex.PoseStack
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * Renders embeddium's compiled terrain (solid and cutout layers) from an arbitrary viewpoint into the currently bound framebuffer.
 *
 * The render lists are the ones embeddium culled against the player's camera, so only sections visible to the player are drawn.
 * This must only be called when embeddium is loaded (see [SodiumPlugin.shouldApply]).
 */
object EmbeddiumTerrainRenderer {
    /**
     * Draws the terrain as seen from [viewOrigin] with the rotation-only view matrix [viewRotation].
     * The caller must set the projection matrix through [com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix] beforehand.
     * Returns false if embeddium's world renderer is not available.
     */
    fun renderTerrain(viewOrigin: Vec3, viewRotation: Matrix4f): Boolean {
        val sodiumRenderer = SodiumWorldRenderer.instanceNullable() ?: return false

        val poseStack = PoseStack()
        poseStack.mulPoseMatrix(viewRotation)

        RenderDevice.enterManagedCode()

        try {
            sodiumRenderer.drawChunkLayer(RenderType.solid(), poseStack, viewOrigin.x, viewOrigin.y, viewOrigin.z)
        } finally {
            RenderDevice.exitManagedCode()
        }

        return true
    }
}
