@file:Suppress("unused")

/**
 * Dynamic light rendering system with shadow map support.
 *
 * Multiple [DynamicLightSource]s can be registered. Each frame, the nearest [DynamicLightManager.MAX_LIGHTS] visible sources to the camera are selected.
 * The camera's view matrix, projection matrix and fog are captured at [RenderLevelStageEvent.Stage.AFTER_ENTITIES].
 * At [RenderLevelStageEvent.Stage.AFTER_LEVEL], the scene is lit as follows:
 * - The main render target's color and depth are copied, because the passes below are not allowed to read from the target they write to.
 * - [SceneAlbedoRenderer] renders the unlit surface colors of the scene, so lit surfaces keep their texture even in complete darkness.
 * - For each light, [ShadowMapRenderer] renders a shadow map and the `dynamic_light` shader accumulates the light into an offscreen target.
 * - The main render target is restored from the copy and the accumulated light is composited onto it with the `dynamic_light_composite` shader.
 *
 * Light source providers (e.g. [org.eln2.mc.common.content.FlashlightItem]) register update callbacks via [DynamicLightManager.addUpdateCallback]
 * to manage their sources each frame. The manager is light-source-agnostic: it only iterates whatever is registered.
 * */
package org.eln2.mc.client.dynamicLight

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.shaders.BlendMode
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.RegisterShadersEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import org.eln2.mc.LOG
import org.eln2.mc.resource
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30

object DynamicLightManager {
    private const val MAX_LIGHTS = 4

    private var lightShader: ShaderInstance? = null
    private var compositeShader: ShaderInstance? = null

    private var sceneTarget: TextureTarget? = null
    private var albedoTarget: TextureTarget? = null
    private var accumulationTarget: TextureTarget? = null

    private val lightSources = mutableListOf<DynamicLightSource>()
    private val updateCallbacks = mutableListOf<(ClientLevel, Float) -> Unit>()

    private val capturedViewMatrix = Matrix4f()
    private val capturedProjectionMatrix = Matrix4f()
    private var capturedFogStart = Float.MAX_VALUE
    private var capturedFogEnd = Float.MAX_VALUE
    private var capturedFogAlpha = 0.0f
    private var capturedFogShape = 0
    private var hasCapturedFrameState = false
    private var hasLoggedRenderFailure = false

    var frameIndex = 0L
        private set

    var isRenderingLevelEntities = false
        private set

    fun register(event: RegisterShadersEvent) {
        val resourceManager = Minecraft.getInstance().resourceManager
        val format = DefaultVertexFormat.POSITION

        event.registerShader(ShaderInstance(resourceManager, resource("dynamic_light"), format)) {
            lightShader = it
            LOG.info("Loaded dynamic light shader.")
        }

        event.registerShader(ShaderInstance(resourceManager, resource("dynamic_light_composite"), format)) {
            compositeShader = it
            LOG.info("Loaded dynamic light composite shader.")
        }
    }

    /**
     * Registers a [DynamicLightSource] with the manager. The source will be rendered each frame if among the nearest [MAX_LIGHTS] to the camera.
     * Returns the source so the caller can modify it (intensity, range, etc.) or unregister it later.
     * */
    fun createLightSource(
        poseUpdater: (Float) -> Pair<Vec3, Vec3>,
        color: Vector3f = Vector3f(1.0f, 0.95f, 0.8f),
        intensity: Float = 0.6f,
        range: Float = 24.0f,
        halfAngleDeg: Float = 30.0f,
        sourceRadius: Float = 0.04f,
        scattering: Float = 0.004f,
        ownerEntity: Entity? = null,
    ): DynamicLightSource {
        val source = DynamicLightSourceImpl(
            poseUpdater,
            color,
            intensity,
            range,
            halfAngleDeg,
            sourceRadius,
            scattering,
            ownerEntity
        )
        lightSources.add(source)
        return source
    }

    /**
     * Removes a [DynamicLightSource] from the manager.
     * */
    fun removeLightSource(source: DynamicLightSource) {
        lightSources.remove(source)
    }

    /**
     * Adds a callback invoked each render frame before rendering lights.
     * The callback receives the [ClientLevel] and partial tick, and should register/unregister its [DynamicLightSource]s as needed.
     * */
    fun addUpdateCallback(callback: (ClientLevel, Float) -> Unit) {
        updateCallbacks.add(callback)
    }

    /**
     * Clears all light sources and update callbacks. Called on level unload / disconnect.
     * */
    fun clear() {
        lightSources.clear()
        updateCallbacks.clear()
    }

    /**
     * Captures the frame state at [RenderLevelStageEvent.Stage.AFTER_ENTITIES] and renders all active light sources at [RenderLevelStageEvent.Stage.AFTER_LEVEL].
     * */
    fun render(event: RenderLevelStageEvent) {
        if (event.stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            frameIndex++
            isRenderingLevelEntities = true
            return
        }

        if (event.stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            isRenderingLevelEntities = false
            captureFrameState(event)
            return
        }

        if (event.stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return
        }

        isRenderingLevelEntities = false

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val camera = event.camera
        val partialTick = event.partialTick

        RenderSystem.assertOnRenderThread()

        for (callback in updateCallbacks) {
            callback(level, partialTick)
        }

        for (source in lightSources) {
            source.updatePose(partialTick)
        }

        if (!hasCapturedFrameState) {
            return
        }

        hasCapturedFrameState = false

        val lightShader = this.lightShader ?: return
        val compositeShader = this.compositeShader ?: return
        val frustum = event.frustum

        val activeSources = lightSources
            .filter { isActive(it, frustum) }
            .sortedBy { it.position.distanceToSqr(camera.position) }
            .take(MAX_LIGHTS)

        if (activeSources.isEmpty()) {
            return
        }

        val savedBlendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val savedBlendDestinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val savedBlendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val savedBlendDestinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val savedBlendEnabled = GL11.glGetBoolean(GL11.GL_BLEND)
        val savedDepthTestEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_TEST)
        val savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val savedShader = RenderSystem.getShader()

        val mainTarget = minecraft.mainRenderTarget
        val sceneTarget = ensureScreenTarget(this.sceneTarget, mainTarget, true)
        val albedoTarget = ensureScreenTarget(this.albedoTarget, mainTarget, true)
        val accumulationTarget = ensureScreenTarget(this.accumulationTarget, mainTarget, false)
        this.sceneTarget = sceneTarget
        this.albedoTarget = albedoTarget
        this.accumulationTarget = accumulationTarget

        RenderSystem.backupProjectionMatrix()

        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushPose()
        modelViewStack.setIdentity()
        RenderSystem.applyModelViewMatrix()

        blitFramebuffer(mainTarget, sceneTarget, GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)

        try {
            renderLights(
                lightShader,
                compositeShader,
                activeSources,
                level,
                camera,
                frustum,
                partialTick,
                mainTarget,
                sceneTarget,
                albedoTarget,
                accumulationTarget
            )
        } catch (exception: Exception) {
            if (!hasLoggedRenderFailure) {
                hasLoggedRenderFailure = true
                LOG.error("Dynamic light rendering failed, the scene is drawn without dynamic lights.", exception)
            }

            blitFramebuffer(sceneTarget, mainTarget, GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
        } finally {
            mainTarget.bindWrite(true)
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.depthFunc(GL11.GL_LEQUAL)

            if (savedDepthTestEnabled) {
                RenderSystem.enableDepthTest()
            } else {
                RenderSystem.disableDepthTest()
            }

            RenderSystem.depthMask(savedDepthMask)

            BlendMode.lastApplied = null
            LightSceneRenderer.resetForeignRenderState()

            if (savedBlendEnabled) {
                RenderSystem.enableBlend()
            } else {
                RenderSystem.disableBlend()
            }

            GlStateManager._blendFuncSeparate(
                savedBlendSourceRgb,
                savedBlendDestinationRgb,
                savedBlendSourceAlpha,
                savedBlendDestinationAlpha
            )

            modelViewStack.popPose()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.restoreProjectionMatrix()

            savedShader?.let { RenderSystem.setShader { it } }
        }
    }

    fun cameraRelativeFromView(viewPosition: Vector3f): Vector3f {
        return Matrix4f(capturedViewMatrix).invert().transformPosition(Vector3f(viewPosition))
    }

    private fun captureFrameState(event: RenderLevelStageEvent) {
        capturedViewMatrix.set(event.poseStack.last().pose())
        capturedProjectionMatrix.set(event.projectionMatrix)
        capturedFogStart = RenderSystem.getShaderFogStart()
        capturedFogEnd = RenderSystem.getShaderFogEnd()
        capturedFogAlpha = RenderSystem.getShaderFogColor()[3]
        capturedFogShape = RenderSystem.getShaderFogShape().index
        hasCapturedFrameState = true
    }

    private fun isActive(source: DynamicLightSource, frustum: Frustum): Boolean {
        if (source.intensity <= 0.0f || source.range <= ShadowMapRenderer.NEAR_PLANE * 2.0f) {
            return false
        }

        if (source.direction.lengthSqr() < 1.0e-8) {
            return false
        }

        val position = source.position
        val range = source.range.toDouble()

        return frustum.isVisible(
            AABB(
                position.x - range,
                position.y - range,
                position.z - range,
                position.x + range,
                position.y + range,
                position.z + range
            )
        )
    }

    private fun renderLights(
        lightShader: ShaderInstance,
        compositeShader: ShaderInstance,
        activeSources: List<DynamicLightSource>,
        level: ClientLevel,
        camera: Camera,
        frustum: Frustum,
        partialTick: Float,
        mainTarget: RenderTarget,
        sceneTarget: TextureTarget,
        albedoTarget: TextureTarget,
        accumulationTarget: TextureTarget,
    ) {
        albedoTarget.copyDepthFrom(mainTarget)

        val frames = activeSources.map { LightSceneRenderer.collectFrame(level, it, partialTick) }
        val cameraView = SceneView(
            camera.position,
            Matrix4f(capturedViewMatrix),
            Matrix4f(capturedProjectionMatrix),
            camera
        )

        SceneAlbedoRenderer.render(albedoTarget, cameraView, frames, frustum, level, partialTick)

        mainTarget.bindWrite(true)
        RenderSystem.depthMask(true)
        RenderSystem.clearDepth(1.0)
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)

        accumulationTarget.bindWrite(true)
        RenderSystem.colorMask(true, true, true, true)
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX)

        val viewProjection = Matrix4f(capturedProjectionMatrix).mul(capturedViewMatrix)
        val inverseViewProjection = Matrix4f(viewProjection).invert()

        for (frame in frames) {
            val shadowView = ShadowMapRenderer.render(frame, level, partialTick)

            drawLightPass(
                lightShader,
                frame.source,
                shadowView,
                camera.position,
                viewProjection,
                inverseViewProjection,
                sceneTarget,
                albedoTarget,
                accumulationTarget
            )
        }

        blitFramebuffer(sceneTarget, mainTarget, GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)

        mainTarget.bindWrite(true)
        prepareFullscreenState()
        RenderSystem.setShader { compositeShader }
        compositeShader.setSampler("LightSampler", accumulationTarget.colorTextureId)
        drawFullscreenQuad()
        BlendMode.lastApplied = null
    }

    private fun drawLightPass(
        shader: ShaderInstance,
        source: DynamicLightSource,
        shadowView: ShadowMapView,
        cameraPosition: Vec3,
        viewProjection: Matrix4f,
        inverseViewProjection: Matrix4f,
        sceneTarget: TextureTarget,
        albedoTarget: TextureTarget,
        accumulationTarget: TextureTarget,
    ) {
        val direction = source.direction.normalize()

        accumulationTarget.bindWrite(true)
        prepareFullscreenState()
        RenderSystem.setShader { shader }

        shader.setSampler("SceneDepthSampler", sceneTarget.depthTextureId)
        shader.setSampler("SceneColorSampler", sceneTarget.colorTextureId)
        shader.setSampler("AlbedoSampler", albedoTarget.colorTextureId)
        shader.setSampler("ShadowDepthSampler", ShadowMapRenderer.depthTextureId)
        shader.setSampler("ShadowCompareSampler", ShadowMapRenderer.compareTextureId)

        shader.safeGetUniform("InverseViewProjectionMatrix").set(inverseViewProjection)
        shader.safeGetUniform("ViewProjectionMatrix").set(viewProjection)
        shader.safeGetUniform("LightViewMatrix").set(shadowView.rotation)
        shader.safeGetUniform("LightPosition").set(
            (source.position.x - cameraPosition.x).toFloat(),
            (source.position.y - cameraPosition.y).toFloat(),
            (source.position.z - cameraPosition.z).toFloat()
        )
        shader.safeGetUniform("LightDirection").set(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
        shader.safeGetUniform("LightColor").set(source.color)
        shader.safeGetUniform("LightIntensity").set(source.intensity)
        shader.safeGetUniform("LightRange").set(source.range)
        shader.safeGetUniform("LightHalfAngle").set(Math.toRadians(source.halfAngleDeg.toDouble()).toFloat())
        shader.safeGetUniform("LightSourceRadius").set(source.sourceRadius)
        shader.safeGetUniform("LightScattering").set(source.scattering)
        shader.safeGetUniform("ShadowTanHalfFieldOfView").set(shadowView.tanHalfFieldOfView)
        shader.safeGetUniform("ShadowNear").set(shadowView.near)
        shader.safeGetUniform("ShadowFar").set(shadowView.far)
        shader.safeGetUniform("ShadowMapSize").set(ShadowMapRenderer.SHADOW_MAP_SIZE.toFloat())
        shader.safeGetUniform("ScreenSize").set(accumulationTarget.width.toFloat(), accumulationTarget.height.toFloat())
        shader.safeGetUniform("FogParameters").set(
            capturedFogStart,
            capturedFogEnd,
            capturedFogAlpha,
            capturedFogShape.toFloat()
        )

        drawFullscreenQuad()
        BlendMode.lastApplied = null
    }

    private fun prepareFullscreenState() {
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.colorMask(true, true, true, true)
        BlendMode.lastApplied = null
        LightSceneRenderer.resetForeignRenderState()
    }

    private fun ensureScreenTarget(
        current: TextureTarget?,
        mainTarget: RenderTarget,
        useDepth: Boolean,
    ): TextureTarget {
        val needsStencil = useDepth && mainTarget.isStencilEnabled

        if (
            current != null &&
            current.width == mainTarget.width &&
            current.height == mainTarget.height &&
            current.isStencilEnabled == needsStencil
        ) {
            return current
        }

        current?.destroyBuffers()

        val target = TextureTarget(mainTarget.width, mainTarget.height, useDepth, Minecraft.ON_OSX)

        if (needsStencil) {
            target.enableStencil()
        }

        target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        return target
    }

    private fun blitFramebuffer(source: RenderTarget, destination: RenderTarget, mask: Int) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId)
        GlStateManager._glBlitFrameBuffer(
            0, 0, source.width, source.height,
            0, 0, destination.width, destination.height,
            mask, GL11.GL_NEAREST
        )
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Draws a fullscreen quad in NDC coordinates (-1..1) with no UVs or normals.
     * */
    private fun drawFullscreenQuad() {
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.builder
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        buffer.vertex(-1.0, -1.0, 0.0).endVertex()
        buffer.vertex(1.0, -1.0, 0.0).endVertex()
        buffer.vertex(1.0, 1.0, 0.0).endVertex()
        buffer.vertex(-1.0, 1.0, 0.0).endVertex()
        BufferUploader.drawWithShader(buffer.end())
    }
}
