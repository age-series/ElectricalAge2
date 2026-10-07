@file:Suppress("unused")

package org.eln2.mc.client.dynamicLight

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexSorting
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import dev.engine_room.flywheel.impl.event.RenderContextImpl
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.mathematics.lerp
import org.eln2.mc.integration.sodium.EmbeddiumTerrainRenderer
import org.eln2.mc.integration.sodium.SodiumPlugin
import org.joml.Matrix4f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class SceneView(
    val origin: Vec3,
    val rotation: Matrix4f,
    val projection: Matrix4f,
    val camera: Camera,
)

class DynamicLightFrame(
    val source: DynamicLightSource,
    val entities: List<Entity>,
    val blockEntities: List<BlockEntity>,
)

object LightSceneRenderer {
    private const val ENTITY_RADIUS_MARGIN = 0.5
    private const val BLOCK_ENTITY_RADIUS = 1.5
    private const val COLLECTION_ANGLE_SCALE = 1.45
    private const val MAXIMUM_COLLECTION_HALF_ANGLE_DEGREES = 85.0

    fun collectFrame(level: ClientLevel, source: DynamicLightSource, partialTick: Float): DynamicLightFrame {
        val halfAngle = min(source.halfAngleDeg * COLLECTION_ANGLE_SCALE, MAXIMUM_COLLECTION_HALF_ANGLE_DEGREES)
        val cone = LightCone(source.position, source.direction, source.range.toDouble(), Math.toRadians(halfAngle))

        return DynamicLightFrame(
            source,
            collectEntities(level, cone, partialTick),
            collectBlockEntities(level, cone)
        )
    }

    private fun collectEntities(level: ClientLevel, cone: LightCone, partialTick: Float): List<Entity> {
        val result = ArrayList<Entity>()

        for (entity in level.entitiesForRendering()) {
            val boundingBox = entity.boundingBox
            val center = boundingBox.center
                .subtract(entity.position())
                .add(interpolatedPosition(entity, partialTick))
            val radius = 0.5 * Vec3(boundingBox.xsize, boundingBox.ysize, boundingBox.zsize).length()

            if (cone.intersectsSphere(center, radius + ENTITY_RADIUS_MARGIN)) {
                result.add(entity)
            }
        }

        return result
    }

    private fun collectBlockEntities(level: ClientLevel, cone: LightCone): List<BlockEntity> {
        val minimumChunkX = Mth.floor(cone.apex.x - cone.range) shr 4
        val maximumChunkX = Mth.floor(cone.apex.x + cone.range) shr 4
        val minimumChunkZ = Mth.floor(cone.apex.z - cone.range) shr 4
        val maximumChunkZ = Mth.floor(cone.apex.z + cone.range) shr 4
        val result = ArrayList<BlockEntity>()

        for (chunkX in minimumChunkX..maximumChunkX) {
            for (chunkZ in minimumChunkZ..maximumChunkZ) {
                val chunk = level.chunkSource.getChunk(chunkX, chunkZ, false) ?: continue

                for (blockEntity in chunk.blockEntities.values) {
                    if (cone.intersectsSphere(blockEntity.blockPos.center, BLOCK_ENTITY_RADIUS)) {
                        result.add(blockEntity)
                    }
                }
            }
        }

        return result
    }

    fun interpolatedPosition(entity: Entity, partialTick: Float): Vec3 {
        return Vec3(
            lerp(entity.xOld, entity.x, partialTick.toDouble()),
            lerp(entity.yOld, entity.y, partialTick.toDouble()),
            lerp(entity.zOld, entity.z, partialTick.toDouble())
        )
    }

    fun drawTerrain(view: SceneView) {
        RenderSystem.setProjectionMatrix(view.projection, VertexSorting.DISTANCE_TO_ORIGIN)

        if (SodiumPlugin.shouldApply()) {
            EmbeddiumTerrainRenderer.renderTerrain(view.origin, view.rotation)
        } else {
            drawVanillaTerrain(view)
        }

        resetForeignRenderState()
    }

    private fun drawVanillaTerrain(view: SceneView) {
        val chunkInfos = Minecraft.getInstance().levelRenderer.renderChunksInFrustum

        for (renderType in arrayOf(RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout())) {
            renderType.setupRenderState()
            val shader = RenderSystem.getShader()

            if (shader != null) {
                for (samplerIndex in 0 until 12) {
                    shader.setSampler("Sampler$samplerIndex", RenderSystem.getShaderTexture(samplerIndex))
                }

                shader.MODEL_VIEW_MATRIX?.set(view.rotation)
                shader.PROJECTION_MATRIX?.set(view.projection)
                shader.COLOR_MODULATOR?.set(RenderSystem.getShaderColor())
                shader.FOG_START?.set(RenderSystem.getShaderFogStart())
                shader.FOG_END?.set(RenderSystem.getShaderFogEnd())
                shader.FOG_COLOR?.set(RenderSystem.getShaderFogColor())
                shader.FOG_SHAPE?.set(RenderSystem.getShaderFogShape().index)
                shader.TEXTURE_MATRIX?.set(RenderSystem.getTextureMatrix())
                shader.GAME_TIME?.set(RenderSystem.getShaderGameTime())
                RenderSystem.setupShaderLights(shader)
                shader.apply()

                val chunkOffset = shader.CHUNK_OFFSET

                for (chunkInfo in chunkInfos) {
                    val renderChunk = chunkInfo.chunk

                    if (renderChunk.compiledChunk.isEmpty(renderType)) {
                        continue
                    }

                    val chunkOrigin = renderChunk.origin

                    if (chunkOffset != null) {
                        chunkOffset.set(
                            (chunkOrigin.x - view.origin.x).toFloat(),
                            (chunkOrigin.y - view.origin.y).toFloat(),
                            (chunkOrigin.z - view.origin.z).toFloat()
                        )
                        chunkOffset.upload()
                    }

                    val vertexBuffer = renderChunk.getBuffer(renderType)
                    vertexBuffer.bind()
                    vertexBuffer.draw()
                }

                chunkOffset?.set(0.0f, 0.0f, 0.0f)
                shader.clear()
                VertexBuffer.unbind()
            }

            renderType.clearRenderState()
        }
    }

    /**
     * Draws all flywheel visuals from [view]. This does not advance any animation state, the instances were prepared during the vanilla level render.
     * Flywheel's order independent transparency pipeline composites onto the main render target and writes its depth, so the caller must restore it afterward.
     * */
    fun drawFlywheel(view: SceneView, level: ClientLevel, partialTick: Float) {
        val visualizationManager = VisualizationManager.get(level) ?: return
        val minecraft = Minecraft.getInstance()

        val poseStack = PoseStack()
        poseStack.mulPoseMatrix(view.rotation)

        val context = RenderContextImpl.create(
            minecraft.levelRenderer,
            level,
            minecraft.renderBuffers(),
            poseStack,
            Matrix4f(view.projection),
            view.camera,
            partialTick
        )

        visualizationManager.renderDispatcher().afterEntities(context)
        resetForeignRenderState()
    }

    /**
     * Draws the non-flywheel [blockEntities] from [view]. If [frustum] is provided, block entities outside of it are skipped.
     * Each block entity is flushed separately and [target] is rebound, because some render types switch the output framebuffer.
     * */
    fun drawBlockEntities(
        view: SceneView,
        blockEntities: Collection<BlockEntity>,
        level: ClientLevel,
        partialTick: Float,
        target: RenderTarget,
        frustum: Frustum?,
    ) {
        if (blockEntities.isEmpty()) {
            return
        }

        val minecraft = Minecraft.getInstance()
        val dispatcher = minecraft.blockEntityRenderDispatcher
        val bufferSource = minecraft.renderBuffers().bufferSource()

        RenderSystem.setProjectionMatrix(view.projection, VertexSorting.DISTANCE_TO_ORIGIN)

        val poseStack = PoseStack()
        poseStack.mulPoseMatrix(view.rotation)

        dispatcher.prepare(level, view.camera, minecraft.hitResult)

        try {
            for (blockEntity in blockEntities) {
                if (VisualizationHelper.skipVanillaRender(blockEntity)) {
                    continue
                }

                if (frustum != null && !frustum.isVisible(blockEntity.renderBoundingBox)) {
                    continue
                }

                val blockPos = blockEntity.blockPos
                poseStack.pushPose()
                poseStack.translate(
                    blockPos.x - view.origin.x,
                    blockPos.y - view.origin.y,
                    blockPos.z - view.origin.z
                )
                dispatcher.render(blockEntity, partialTick, poseStack, bufferSource)
                poseStack.popPose()

                bufferSource.endBatch()
                target.bindWrite(false)
            }
        } finally {
            bufferSource.endBatch()
            dispatcher.prepare(level, minecraft.gameRenderer.mainCamera, minecraft.hitResult)
            target.bindWrite(false)
        }
    }

    /**
     * Draws the non-flywheel [entities] from [view], without blob shadows or hitboxes.
     * If [hideNameTags] is true, name tags are suppressed so they do not occlude light.
     * Each entity is flushed separately and [target] is rebound, because some render types switch the output framebuffer.
     * */
    fun drawEntities(
        view: SceneView,
        entities: Collection<Entity>,
        level: ClientLevel,
        partialTick: Float,
        target: RenderTarget,
        hideNameTags: Boolean,
    ) {
        if (entities.isEmpty()) {
            return
        }

        val minecraft = Minecraft.getInstance()
        val dispatcher = minecraft.entityRenderDispatcher
        val bufferSource = minecraft.renderBuffers().bufferSource()
        val savedShouldRenderHitBoxes = dispatcher.shouldRenderHitBoxes()
        val savedHideGui = minecraft.options.hideGui

        RenderSystem.setProjectionMatrix(view.projection, VertexSorting.DISTANCE_TO_ORIGIN)

        val poseStack = PoseStack()
        poseStack.mulPoseMatrix(view.rotation)

        dispatcher.setRenderShadow(false)
        dispatcher.setRenderHitBoxes(false)
        dispatcher.prepare(level, view.camera, minecraft.crosshairPickEntity)

        if (hideNameTags) {
            minecraft.options.hideGui = true
        }

        try {
            for (entity in entities) {
                if (VisualizationHelper.skipVanillaRender(entity)) {
                    continue
                }

                val position = interpolatedPosition(entity, partialTick)
                val yaw = Mth.lerp(partialTick, entity.yRotO, entity.yRot)
                val packedLight = dispatcher.getPackedLightCoords(entity, partialTick)

                dispatcher.render(
                    entity,
                    position.x - view.origin.x,
                    position.y - view.origin.y,
                    position.z - view.origin.z,
                    yaw,
                    partialTick,
                    poseStack,
                    bufferSource,
                    packedLight
                )

                bufferSource.endBatch()
                target.bindWrite(false)
            }
        } finally {
            bufferSource.endBatch()
            minecraft.options.hideGui = savedHideGui
            dispatcher.setRenderShadow(true)
            dispatcher.setRenderHitBoxes(savedShouldRenderHitBoxes)
            dispatcher.prepare(level, minecraft.gameRenderer.mainCamera, minecraft.crosshairPickEntity)
            target.bindWrite(false)
        }
    }

    fun resetForeignRenderState() {
        BufferUploader.invalidate()
        GameRenderer.getPositionShader()?.clear()
    }

    fun yawPitchOf(direction: Vec3): Pair<Float, Float> {
        val horizontalLength = sqrt(direction.x * direction.x + direction.z * direction.z)
        val yaw = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
        val pitch = Math.toDegrees(atan2(-direction.y, horizontalLength)).toFloat()
        return Pair(yaw, pitch)
    }

    fun viewRotation(yaw: Float, pitch: Float): Matrix4f {
        return Matrix4f()
            .rotationX(Math.toRadians(pitch.toDouble()).toFloat())
            .rotateY(Math.toRadians((yaw + 180.0f).toDouble()).toFloat())
    }
}

private class LightCone(val apex: Vec3, direction: Vec3, val range: Double, halfAngle: Double) {
    private val axis = direction.normalize()
    private val cosHalfAngle = cos(halfAngle)
    private val sinHalfAngle = sin(halfAngle)

    fun intersectsSphere(center: Vec3, radius: Double): Boolean {
        val offset = center.subtract(apex)
        val distanceSquared = offset.lengthSqr()
        val reach = range + radius

        if (distanceSquared > reach * reach) {
            return false
        }

        val axialDistance = offset.dot(axis)

        if (axialDistance < -radius) {
            return false
        }

        val perpendicularDistance = sqrt(max(distanceSquared - axialDistance * axialDistance, 0.0))

        return perpendicularDistance * cosHalfAngle - axialDistance * sinHalfAngle <= radius
    }
}

/**
 * A [Camera] placed at a light source, used to prepare the vanilla dispatchers and flywheel's render context.
 * */
class LightCamera(position: Vec3, yaw: Float, pitch: Float) : Camera() {
    init {
        setPosition(position)
        setRotation(yaw, pitch)
    }
}
