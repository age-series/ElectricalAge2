@file:Suppress("unused")

package org.eln2.mc.client.dynamicLight

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * A dynamic light source registered with [DynamicLightManager].
 *
 * All properties are mutable so the holder can update them each frame (e.g. intensity modulated by power, position tracked to a player).
 * [updatePose] is called by the manager before rendering to refresh [position] and [direction] from the pose callback.
 * */
interface DynamicLightSource {
    var position: Vec3
    var direction: Vec3
    var color: Vector3f
    var intensity: Float
    var range: Float
    var halfAngleDeg: Float
    var sourceRadius: Float
    var scattering: Float
    var ownerEntity: Entity?

    /**
     * Called by [DynamicLightManager] each render frame with the current partial tick.
     * Implementations should update [position] and [direction] from whatever entity or transform they track.
     * */
    fun updatePose(partialTick: Float)
}

/**
 * Concrete [DynamicLightSource] backed by a pose-update lambda.
 *
 * The [poseUpdater] receives the partial tick and returns a [Pair] of (position, direction).
 * This decouples the light from any specific entity type: a flashlight passes a lambda that reads the holding player,
 * a static test light passes a lambda that returns a fixed pose.
 * */
@Suppress("CanBePrimaryConstructorProperty")
class DynamicLightSourceImpl(
    private val poseUpdater: (Float) -> Pair<Vec3, Vec3>,
    color: Vector3f,
    intensity: Float,
    range: Float,
    halfAngleDeg: Float,
    sourceRadius: Float,
    scattering: Float,
    ownerEntity: Entity?,
) : DynamicLightSource {
    override var position: Vec3 = Vec3.ZERO
    override var direction: Vec3 = Vec3(0.0, 0.0, -1.0)
    override var color: Vector3f = color
    override var intensity: Float = intensity
    override var range: Float = range
    override var halfAngleDeg: Float = halfAngleDeg
    override var sourceRadius: Float = sourceRadius
    override var scattering: Float = scattering
    override var ownerEntity: Entity? = ownerEntity

    override fun updatePose(partialTick: Float) {
        val (pos, dir) = poseUpdater(partialTick)
        position = pos
        direction = dir
    }
}
