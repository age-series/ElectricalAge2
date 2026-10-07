@file:Suppress("unused")

package org.eln2.mc.client.dynamicLight

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import org.eln2.mc.LOG
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import kotlin.math.tan

class ShadowMapView(
    val rotation: Matrix4f,
    val tanHalfFieldOfView: Float,
    val near: Float,
    val far: Float,
)

/**
 * Renders a shadow map from the light's point of view.
 *
 * Terrain is rendered by embeddium if it is loaded, or by the vanilla chunk renderer otherwise.
 * Flywheel visuals, vanilla block entities and vanilla entities within the light's cone are rendered as well.
 * The light's owner entity is skipped so the holder does not shadow their own light.
 *
 * Two depth textures are produced: [depthTextureId] holds raw depth for the blocker search,
 * and [compareTextureId] is a copy with hardware depth comparison and linear filtering enabled for percentage-closer filtering.
 */
object ShadowMapRenderer {
    const val SHADOW_MAP_SIZE = 2048
    const val NEAR_PLANE = 0.1f
    private const val FIELD_OF_VIEW_MARGIN_DEGREES = 2.0f

    private var renderTarget: TextureTarget? = null
    private var compareTarget: TextureTarget? = null

    val depthTextureId: Int
        get() = renderTarget?.depthTextureId ?: 0

    val compareTextureId: Int
        get() = compareTarget?.depthTextureId ?: 0

    private fun ensureTargets(): TextureTarget {
        val existing = renderTarget

        if (existing != null && compareTarget != null) {
            return existing
        }

        val target = TextureTarget(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true, Minecraft.ON_OSX)
        target.setClearColor(1.0f, 1.0f, 1.0f, 1.0f)

        val comparison = TextureTarget(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true, Minecraft.ON_OSX)
        GlStateManager._bindTexture(comparison.depthTextureId)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL)
        GlStateManager._bindTexture(0)

        renderTarget = target
        compareTarget = comparison
        LOG.info("Created dynamic light shadow map targets ${SHADOW_MAP_SIZE}x${SHADOW_MAP_SIZE}")

        return target
    }

    /**
     * Renders the shadow map for the light of [frame] and returns the view the shader needs to sample it.
     */
    fun render(frame: DynamicLightFrame, level: ClientLevel, partialTick: Float): ShadowMapView {
        RenderSystem.assertOnRenderThread()

        val source = frame.source
        val target = ensureTargets()
        val comparison = compareTarget!!

        val halfFieldOfView = Math.toRadians((source.halfAngleDeg + FIELD_OF_VIEW_MARGIN_DEGREES).toDouble()).toFloat()
        val projection = Matrix4f().setPerspective(halfFieldOfView * 2.0f, 1.0f, NEAR_PLANE, source.range)
        val (yaw, pitch) = LightSceneRenderer.yawPitchOf(source.direction)
        val rotation = LightSceneRenderer.viewRotation(yaw, pitch)
        val view = SceneView(source.position, rotation, projection, LightCamera(source.position, yaw, pitch))

        RenderSystem.depthMask(true)
        RenderSystem.colorMask(true, true, true, true)
        target.clear(Minecraft.ON_OSX)

        prepareState(target)
        LightSceneRenderer.drawTerrain(view)

        prepareState(target)
        LightSceneRenderer.drawFlywheel(view, level, partialTick)

        prepareState(target)
        LightSceneRenderer.drawBlockEntities(view, frame.blockEntities, level, partialTick, target, null)

        prepareState(target)
        val occludingEntities = frame.entities.filter { it !== source.ownerEntity }
        LightSceneRenderer.drawEntities(view, occludingEntities, level, partialTick, target, true)

        RenderSystem.colorMask(true, true, true, true)
        comparison.copyDepthFrom(target)

        return ShadowMapView(rotation, tan(halfFieldOfView), NEAR_PLANE, source.range)
    }

    private fun prepareState(target: TextureTarget) {
        target.bindWrite(true)
        RenderSystem.enableDepthTest()
        RenderSystem.depthFunc(GL11.GL_LEQUAL)
        RenderSystem.depthMask(true)
        RenderSystem.colorMask(false, false, false, false)
        RenderSystem.disableBlend()
    }
}
