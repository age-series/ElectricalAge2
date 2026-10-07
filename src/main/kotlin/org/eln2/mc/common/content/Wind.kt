package org.eln2.mc.common.content

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.event.TickEvent.Phase
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.BoundingBoxTree3d
import org.ageseries.libage.data.Distance
import org.ageseries.libage.data.LocatorBuilder
import org.ageseries.libage.data.NEWTON_METER
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.put
import org.ageseries.libage.data.requireLocator
import org.ageseries.libage.data.registerHandler
import net.minecraft.world.level.block.entity.BlockEntity
import org.ageseries.libage.mathematics.geometry.*
import org.ageseries.libage.mathematics.lerp
import org.ageseries.libage.mathematics.map
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.kinetic.KineticMono
import org.ageseries.libage.sim.kinetic.KineticNodeSet
import org.ageseries.libage.utils.addUnique
import org.eln2.mc.*
import org.eln2.mc.client.render.DebugVisualizer
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.foundation.BasicKineticPart
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.eln2.mc.common.blocks.foundation.BigBlockRepresentativeBlockEntity
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.MultiblockDelegateMap
import org.eln2.mc.common.blocks.foundation.MultiblockTransformations
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.common.cells.foundation.CellAndContainerHandle
import org.eln2.mc.common.cells.foundation.CellCreateInfo
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.InternalKineticReplicatorBehavior
import org.eln2.mc.common.cells.foundation.InternalKineticStateConsumer
import org.eln2.mc.common.cells.foundation.KineticInterpolatorClient
import org.eln2.mc.common.cells.foundation.KineticObject
import org.eln2.mc.common.cells.foundation.KineticSize
import org.eln2.mc.common.cells.foundation.PersistentObject
import org.eln2.mc.common.cells.foundation.Replicator
import org.eln2.mc.common.cells.foundation.RotatingKineticState
import org.eln2.mc.common.cells.foundation.SidedKinetic
import org.eln2.mc.common.cells.foundation.SimObject
import org.eln2.mc.common.cells.foundation.SubscriberCollection
import org.eln2.mc.common.cells.foundation.SimulationPhase
import org.eln2.mc.common.cells.foundation.addPre
import org.eln2.mc.common.cells.foundation.pipelikeCellScan
import org.eln2.mc.common.content.modules.Eln2Kinetic
import org.eln2.mc.common.events.Scheduler
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.Locators
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import org.eln2.mc.common.blocks.foundation.ReplaceVanillaParticlesBlockExtension
import org.eln2.mc.extensions.cast
import org.eln2.mc.extensions.loadNbt
import org.eln2.mc.extensions.minus
import org.eln2.mc.extensions.saveNbt
import org.eln2.mc.extensions.toVector3d
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.BlockPosInt
import org.eln2.mc.mathematics.floorBlockPos
import org.joml.SimplexNoise
import java.util.*
import java.util.function.Consumer
import kotlin.math.*

private const val WIND_TURBINE_DEBUG_DRAW = false

/**
 * We hold the turbine handles in the cells.
 * This allows turbines which are not chunk-loaded to keep functioning.
 * */
@ServerOnly @CrossThreadAccess
object WindSystem {
    private val levels = HashMap<ServerLevel, LevelWindData>()
    private val obj = Any()

    @OnServerThread
    fun clear() {
        synchronized(obj) {
            levels.clear()
        }
    }

    private fun validateLevel(level: Level): ServerLevel {
        if(level !is ServerLevel) {
            error("Cannot use non server level $level")
        }

        return level
    }

    private fun getLevelDataNonSynchronized(level: Level) : LevelWindData {
        val serverLevel = validateLevel(level)

        return levels.computeIfAbsent(serverLevel) {
            LevelWindData(serverLevel,
                WindParameters(
                    5e-5,
                    7e-4,
                    0.3,
                    0.45,
                    0.9,
                    1.12,
                    24.0
                )
            )
        }
    }

    /**
     * Creates a wind turbine with the specified area of influence.
     * */
    @CrossThreadAccess
    fun createWindTurbineHandle(level: ServerLevel, volumeOfInfluenceWorld: BoundingBox3d, deviceBoundsWorld: BoundingBox3d) : WindTurbineHandle {
        synchronized(obj) {
            val data = getLevelDataNonSynchronized(level)
            return data.createTurbine(volumeOfInfluenceWorld, deviceBoundsWorld)
        }
    }

    /**
     * Destroys the wind turbine.
     * */
    @CrossThreadAccess
    fun destroyWindTurbineHandle(handle: WindTurbineHandle) {
        synchronized(obj) {
            val data = getLevelDataNonSynchronized(handle.level)
            data.destroyTurbine(handle)
        }
    }

    @OnServerThread
    fun handleBlockEvent(level: ServerLevel, blockPos: BlockPos) {
        synchronized(obj) {
            val data = getLevelDataNonSynchronized(level)
            data.handleBlockEvent(blockPos)
        }
    }

    @OnServerThread
    fun update() {
        synchronized(obj) {
            levels.values.forEach {
                it.update()
            }
        }
    }

    /**
     * Wind turbine volume, that has its [clearanceFactor] updated by the manager.
     * */
    interface WindTurbineHandle {
        /**
         * The level of the cell that created this handle.
         * */
        val level: ServerLevel

        /**
         * Unique ID created by the manager for this turbine.
         * */
        val id: UUID

        /**
         * The volume of influence of this turbine (includes the [deviceBounds]).
         * */
        val volumeOfInfluence: BoundingBox3d

        /**
         * The volume of the device itself.
         * */
        val deviceBounds: BoundingBox3d

        /**
         * Gets the clearance of this wind turbine. If the turbine is all clear, the factor is `1`.
         * If other turbines are close-by or blocks exist in the volume, this clearance factor goes down.
         *
         * When the wind turbine is created initially (possibly on the simulation thread), the factor is `0`.
         * */
        val clearanceFactor: Double

        /**
         * Gets the wind speed at the turbine, based on the [clearanceFactor] and the level's wind speed.
         * */
        val windSpeed: Double
    }

    private class WindTurbineVolumeImpl(
        override val id: UUID,
        override val volumeOfInfluence: BoundingBox3d,
        override val deviceBounds: BoundingBox3d,
        val owner: LevelWindData
    ) : WindTurbineHandle {
        override val level: ServerLevel
            get() = owner.level

        /**
         * The clearance, taking into account only blocks in the vicinity.
         * Initially set to `0` because we cannot access the blocks from the caller's thread.
         * See [serverThreadInitialization].
         * */
        var blockClearanceFactor = 0.0

        /**
         * The clearance, taking into account only other turbines in the vicinity.
         * Computed fully on insert.
         * */
        var turbineClearanceFactor = 0.0

        /**
         * A relative block pos for storing packed coordinates.
         * */
        val referencePosition = volumeOfInfluence.min.floorBlockPos()

        /**
         * Blocks that intersect the [volumeOfInfluence].
         * Stored as [org.eln2.mc.mathematics.BlockPosInt] relative to the [referencePosition].
         * */
        val blocksInVolume = IntOpenHashSet()

        /**
         * SVO kept up-to-date with [blocksInVolume]. Used for raycasting against the (likely) sparse block grid.
         * */
        val blockSVO = BitSparseVoxelOctree(
            ceil(
                log2(
                    maxOf(
                        volumeOfInfluence.width,
                        volumeOfInfluence.height,
                        volumeOfInfluence.depth
                    ) + 1
                )
            ).toInt()
        )

        /**
         * Other turbines whose bounds intersect the [volumeOfInfluence].
         * */
        val turbinesInVolume = HashSet<WindTurbineVolumeImpl>()

        /**
         * The final clearance factor.
         * */
        override val clearanceFactor get() = blockClearanceFactor * turbineClearanceFactor

        override val windSpeed get() = clearanceFactor * owner.windSpeed

        /**
         * Set by [blockEvent]. Basically, when a recompute is needed, a task is scheduled to run and the flag is set.
         * But if multiple block events happen sequentially, this flag is checked so multiple tasks are not created.
         * */
        private var recomputeScheduled = false

        /**
         * Gets the block position as a packed position relative to [referencePosition] for [blocksInVolume].
         * */
        private fun getBlockKey(blockPosWorld: BlockPos) = BlockPosInt.of(blockPosWorld - referencePosition)

        /**
         * Checks if the block at the specified position is a solid block that occludes the wind.
         * */
        private fun isOccluding(blockPosWorld: BlockPos) =  !owner.level.getBlockState(blockPosWorld).isAir

        /**
         * Executed on the server thread once this turbine has been created.
         * */
        @OnServerThread
        fun serverThreadInitialization() {
            requireIsOnServerThread()

            // Guard against some stray events firing before this:
            blocksInVolume.clear()
            blockSVO.clear()

            BlockPos.betweenClosedStream(volumeOfInfluence.cast()).forEach { blockPosWorld ->
                if(!deviceBounds.contains(Vector3d(blockPosWorld.x + 0.5, blockPosWorld.y + 0.5, blockPosWorld.z + 0.5))) {
                    if(isOccluding(blockPosWorld)) {
                        val key = getBlockKey(blockPosWorld)
                        blocksInVolume.addUnique(key.value)
                        check(blockSVO.insert(key.x, key.y, key.z))
                    }
                }
            }

            recomputeBlockOcclusion()
        }

        /**
         * Called when a block has changed in the world, that is inside the volume.
         * This either inserts or remove a block obstruction.
         * The block occlusion is then re-calculated.
         * */
        @OnServerThread
        fun blockEvent(blockPosWorld: BlockPos) {
            requireIsOnServerThread()

            if(deviceBounds.contains(Vector3d(blockPosWorld.x + 0.5, blockPosWorld.y + 0.5, blockPosWorld.z + 0.5))) {
                return
            }

            val isOccluding = !owner.level.getBlockState(blockPosWorld).isAir
            val key = getBlockKey(blockPosWorld)

            fun scheduleRecompute() {
                if(!recomputeScheduled) {
                    recomputeScheduled = true
                    Scheduler.scheduleWork(0, {
                        recomputeBlockOcclusion()
                        recomputeScheduled = false
                    }, Phase.END)
                }
            }

            if(isOccluding) {
                if(blocksInVolume.add(key.value)) {
                    check(blockSVO.insert(key.x, key.y, key.z))
                    scheduleRecompute()
                }
            }
            else {
                if(blocksInVolume.remove(key.value)) {
                    check(blockSVO.remove(key.x, key.y, key.z))
                    scheduleRecompute()
                }
            }
        }

        /**
         * Recomputes [blockClearanceFactor] from scratch using the [blockSVO].
         * */
        private fun recomputeBlockOcclusion() {
            val minY = floor(deviceBounds.min.y).toInt()
            val maxY = floor(deviceBounds.max.y).toInt()

            val radius = ceil(sqrt(volumeOfInfluence.width * volumeOfInfluence.width + volumeOfInfluence.depth * volumeOfInfluence.depth) / 2.0 + 1.0)

            val rayX = deviceBounds.center.x - referencePosition.x
            val rayZ = deviceBounds.center.z - referencePosition.z

            var hitRays = 0
            var totalRays = 0

            val samplesPerSlice = 256

            for (ySlice in minY..maxY) {
                val rayY = ySlice.toDouble() - referencePosition.y + 0.5

                repeat(samplesPerSlice) { angleIdx ->
                    val angle = map(
                        angleIdx.toDouble(),
                        0.0, samplesPerSlice.toDouble(), // Correct (so we don't get two identical samples)
                        0.0,  2.0 * PI
                    )

                    val ray = Ray3d(
                        Vector3d(rayX, rayY, rayZ),
                        Vector3d(cos(angle), 0.0, sin(angle))
                    )

                    val rayHit = blockSVO.raycastIntersectsOrderedDepthFirst(ray, Double.MAX_VALUE)

                    if(WIND_TURBINE_DEBUG_DRAW) {
                        val version = blockSVO.version

                        DebugVisualizer
                            .lineCylinder(
                                Cylinder3d(
                                    Line3d.fromStartEnd(
                                        (ray.origin + referencePosition.toVector3d()),
                                        (ray.origin + referencePosition.toVector3d()) + ray.direction * radius
                                    ),
                                    0.025
                                ),
                                color = if(rayHit) MyColor.RED else MyColor.GREEN
                            )
                            .removeAfter(5.0)
                            .withRemover { blockSVO.version != version }
                    }

                    if(rayHit) {
                        hitRays++
                    }

                    totalRays++
                }
            }

            val blockOcclusion = hitRays.toDouble() / totalRays

            blockClearanceFactor = 1.0 - blockOcclusion

            if(WIND_TURBINE_DEBUG_DRAW) {
                val version = blockSVO.version

                val composite = DebugVisualizer.CompositeRenderElement()

                blockSVO.traverseNodes { node, lc, posSVO, log ->
                    val min = Vector3d(
                        posSVO.x + referencePosition.x,
                        posSVO.y + referencePosition.y,
                        posSVO.z + referencePosition.z
                    )

                    val max = min + (1 shl log).toDouble()

                    val color = if(BitSparseVoxelOctree.getIsFilled(node) || log == 0) {
                        MyColor(255, 255, 255, 0)
                    }
                    else {
                        MyColor.WHITE
                    }

                    composite.with(
                        DebugVisualizer.createLineBox(
                            BoundingBox3d(min, max), color = color
                        )
                    )
                }

                DebugVisualizer.add(composite
                    .removeAfter(10.0)
                    .withRemover { blockSVO.version != version }
                )
            }
        }

        /**
         * Recomputes [turbineClearanceFactor] with all [turbinesInVolume].
         * */
        fun recomputeTurbineObstruction() {
            var result = 1.0
            val turbineVolume = volumeOfInfluence.width * volumeOfInfluence.height * volumeOfInfluence.depth

            turbinesInVolume.forEach { other ->
                val intersection = other.volumeOfInfluence intersectionWith volumeOfInfluence
                val intersectedVolume = intersection.width * intersection.height * intersection.depth

                result *= 1.0 - (intersectedVolume / turbineVolume)
            }

            turbineClearanceFactor = result
        }
    }

    data class WindParameters(
        val calmTimeScale: Double,
        val stormTimeScale: Double,
        val minCalmWind: Double,
        val maxCalmWind: Double,
        val minStormWind: Double,
        val maxStormWind: Double,
        val windSpeedScale: Double
    )

    private class LevelWindData(val level: ServerLevel, val parameters: WindParameters) {
        /**
         * BVH built on the [WindTurbineVolumeImpl.volumeOfInfluence].
         * */
        val influenceHierarchy = BoundingBoxTree3d<WindTurbineVolumeImpl>()
        val turbines = HashSet<WindTurbineVolumeImpl>()

        /**
         * The wind speed of this entire level.
         * */
        var windSpeed = 0.0
            private set

        private inline fun findIntersections(bounds: BoundingBox3d, consumer: (WindTurbineVolumeImpl) -> Unit) {
            influenceHierarchy.queryIntersecting({ bounds intersectsWith it.box }) { leaf ->
                val other = requireNotNull(leaf.data) { DEBUGGER_BREAK()  }
                consumer(other)
                true
            }
        }

        /**
         * Creates a new turbine volume.
         * This immediately finds all intersected turbines and records the intersections, then calculates the [WindTurbineVolumeImpl.turbineClearanceFactor].
         *
         * Then, a task is scheduled to run on the server thread to find all blocks in intersection. Only then, the block clearance is calculated, which results in a nonzero final clearance.
         * */
        fun createTurbine(volumeOfInfluenceWorld: BoundingBox3d, deviceBoundsWorld: BoundingBox3d) : WindTurbineHandle {
            val result = WindTurbineVolumeImpl(
                UUID.randomUUID(),
                volumeOfInfluenceWorld,
                deviceBoundsWorld,
                this
            )

            /**
             * Finds all intersections between turbines and records them.
             * */
            findIntersections(volumeOfInfluenceWorld) { other ->
                // Add intersection pair:
                other.turbinesInVolume.addUnique(result)
                result.turbinesInVolume.addUnique(other)

                // Recompute obstruction for remote turbine:
                other.recomputeTurbineObstruction()
            }

            // Recompute obstruction for new turbine:
            result.recomputeTurbineObstruction()

            influenceHierarchy.insert(result, volumeOfInfluenceWorld)
            turbines.addUnique(result)

            /**
             * Schedule a complete scan of the in-world blocks:
             * */
            Scheduler.scheduleWork(
                0,
                result::serverThreadInitialization,
                Phase.END
            )

            return result
        }

        /**
         * Removes the turbine volume and re-calculates the obstruction factors for the other turbines immediately.
         * */
        fun destroyTurbine(handle: WindTurbineHandle) {
            val impl = requireNotNull(handle as? WindTurbineVolumeImpl) {
                DEBUGGER_BREAK(handle)
            }

            if(!turbines.remove(impl)) {
                return
            }

            influenceHierarchy.remove(impl)

            /**
             * Removes the intersection pairs and recomputes the turbine clearance:
             * */
            findIntersections(impl.volumeOfInfluence) { other ->
                check(other.turbinesInVolume.remove(impl)) {
                    DEBUGGER_BREAK("Other turbine did not have removed turbine recorded")
                }

                other.recomputeTurbineObstruction()
            }
        }

        /**
         * Handles a block event sent from the server thread from forge.
         * */
        @OnServerThread
        fun handleBlockEvent(blockPos: BlockPos) {
            val min = blockPos.toVector3d()
            val bounds = BoundingBox3d(min, min + Vector3d.one)

            /**
             * Updates scores for each turbine volume that intersects the block:
             * */
            findIntersections(bounds) { turbine ->
                turbine.blockEvent(blockPos)
            }
        }

        @OnServerThread
        fun update() {
            val rain = level.getRainLevel(1.0f).toDouble()
            val thunder = level.getThunderLevel(1.0f).toDouble()
            val weatherIntensity = max(rain, thunder)

            val gameTime = level.gameTime.toDouble()

            fun noise(input: Double): Double {
                val noise = SimplexNoise
                    .noise(input.toFloat(), 1.0f / 137.0f).toDouble()
                    .coerceIn(-1.0, 1.0)

                return map(
                    noise,
                    -1.0, 1.0,
                    0.0, 1.0
                )
            }

            val calmNoise = noise(gameTime * parameters.calmTimeScale)
            val stormNoise = noise(gameTime * parameters.stormTimeScale)

            val calmWindFactor = lerp(
                parameters.minCalmWind,
                parameters.maxCalmWind,
                calmNoise
            )

            val stormWindFactor = lerp(
                parameters.minStormWind,
                parameters.maxStormWind,
                stormNoise
            )

            val finalWindFactor = lerp(
                calmWindFactor,
                stormWindFactor,
                weatherIntensity
            )

            windSpeed = finalWindFactor * parameters.windSpeedScale
        }
    }
}

/**
 * All simulation data used by the wind turbine.
 * @param volumeOfInfluence The volume that needs to be 100% clear for the turbine to be operating at nominal output. Expected centered at `0`.
 * @param deviceBounds The bounds of the turbine itself (the wind receiver). Expected centered at `0`.
 * @param effectiveRadius The approximate average distance from the center of rotation to the wind-catching surface.
 * @param windEffectivenessFactor Factor that includes the device's surface area, air density, and the aerodynamic efficiency. A higher value means the wind applies more force (the turbine is better).
 * */
data class WindTurbineOptions(
    val volumeOfInfluence: BoundingBox3d,
    val deviceBounds: BoundingBox3d,
    val kineticDescription: FrictionNodeDescription,
    val effectiveRadius: Quantity<Distance>,
    val windEffectivenessFactor: Double
) {
    init {
        require(volumeOfInfluence.center.approxEq(Vector3d.zero)) {
            "Expected volume of influence definition to be centered at 0"
        }

        require(deviceBounds.center.approxEq(Vector3d.zero)) {
            "Expected device bounds definition to be centered at 0"
        }
    }
}

class WindTurbineObject(cell: WindTurbineCell) : KineticObject<WindTurbineCell>(cell), PersistentObject {
    val node = KineticMono()

    init {
        cell.options.kineticDescription.applyTo(node)
        node.extension.ratio *= -1.0
    }

    override fun addNodes(builder: KineticNodeSet) { builder.add(node) }

    override fun offerExtension(remote: KineticObject<*>) = node.extension

    override fun saveObjectNbt() = node.saveNbt()

    override fun loadObjectNbt(tag: CompoundTag) { node.loadNbt(tag) }
}

class WindTurbineCell(ci: CellCreateInfo, val options: WindTurbineOptions) : Cell(ci), SidedKinetic<WindTurbineCell> {
    val volumeOfInfluenceWorld: BoundingBox3d
    val deviceBoundsWorld: BoundingBox3d

    init {
        val baseOffset = locator.requireLocator(Locators.BLOCK).toVector3d() +
            Vector3d(0.5, 1.0, 0.5)

        val volumeOfInfluenceOffset = Vector3d(0.0, options.volumeOfInfluence.height / 2.0, 0.0)
        val deviceBoundsOffset = Vector3d(0.0, options.deviceBounds.height / 2.0, 0.0)

        volumeOfInfluenceWorld = BoundingBox3d(
            options.volumeOfInfluence.min + baseOffset + volumeOfInfluenceOffset,
            options.volumeOfInfluence.max + baseOffset + volumeOfInfluenceOffset
        )

        deviceBoundsWorld = BoundingBox3d(
            options.deviceBounds.min + baseOffset + deviceBoundsOffset,
            options.deviceBounds.max + baseOffset + deviceBoundsOffset
        )

        if(WIND_TURBINE_DEBUG_DRAW) {
            DebugVisualizer
                .lineBox(volumeOfInfluenceWorld, color = MyColor.BLUE)
                .withinScopeOf(this)

            DebugVisualizer
                .lineBox(deviceBoundsWorld, color = MyColor.WHITE)
                .withinScopeOf(this)
        }
    }

    var handle: WindSystem.WindTurbineHandle? = null

    @SimObject
    val kinetic = WindTurbineObject(this)

    /**
     * Gets the last applied torque generated by the wind.
     * */
    var lastWindTorque = 0.0
        private set

    @Replicator
    fun replicator(target: InternalKineticStateConsumer) = InternalKineticReplicatorBehavior(
        RotatingKineticState.accessor(kinetic.node),
        target,
        this,
        kinetic.node::simulation
    )

    override fun getKineticSizeOnSide(side: Base6Direction3d, targetCell: Cell) = when(side) {
        Base6Direction3d.Down -> KineticSize.Standard
        else -> null
    }

    /**
     * Creates the handle for the turbine.
     * */
    override fun onBuildFinished() {
        val level = graph.level
        val handle = handle

        if(handle == null || handle.level != level) {
            this.handle = WindSystem.createWindTurbineHandle(
                level,
                volumeOfInfluenceWorld,
                deviceBoundsWorld
            )
        }

        super.onBuildFinished()
    }

    /**
     * Destroys the handle.
     * */
    override fun onDestroyed() {
        val handle = handle

        if(handle != null) {
            WindSystem.destroyWindTurbineHandle(handle)
            this.handle = null
        }

        super.onDestroyed()
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::tick)
    }

    private fun tick(dt: Double, phase: SimulationPhase) {
        val handle = handle
            ?: return

        val windSpeed = handle.windSpeed

        val bladeSpeed = kinetic.node.angularVelocity * !options.effectiveRadius
        lastWindTorque = options.windEffectivenessFactor * !options.effectiveRadius * windSpeed * (windSpeed - bladeSpeed)

        kinetic.node.externalTorque += lastWindTorque
    }
}

data class WindTurbine3dModel(
    val baseModel: PartialModel,
    val rotorMode: PartialModel
)

class WindTurbineBlock(
    private val cellProvider: RegistryObject<CellProvider<WindTurbineCell>>,
    val delegateMap: MultiblockDelegateMap,
    val model: WindTurbine3dModel
) : UprightHorizontalDirectionCellBlock<WindTurbineCell>() {
    @Deprecated("Deprecated in Java", ReplaceWith("true"))
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean = true

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    override fun getCellProvider() = cellProvider.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = WindTurbineBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (pLevel.isClientSide) {
            return BlockEntityTicker { _, _, _, pBlockEntity ->
                if (pBlockEntity is WindTurbineBlockEntity) {
                    pBlockEntity.clientTick()
                }
            }
        }

        return null
    }

    override fun appendLocatorData(state: BlockState, builder: LocatorBuilder) {
        super.appendLocatorData(state, builder)

        builder.put(Locators.PIPELIKE_MASK, Base6Direction3dMask.DOWN)
    }

    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        pipelikeCellScan(level, cell, results::add)
    }
}

private const val WIND_TURBINE_REFERENCE_OMEGA = 10.0

class WindTurbineBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity<WindTurbineCell>(pos, state, Eln2Kinetic.WIND_TURBINE_BLOCK_ENTITY.get()),
    BigBlockRepresentativeBlockEntity<LampPoleBlockEntity>,
    InternalKineticStateConsumer,
    BulkPacketHandlerBlockEntity,
    ComponentDisplay
{
    @ClientOnly
    var renderState: BasicKineticPart.RenderStateImpl? = null

    @ClientOnly
    private val angularVelocitySmoother = FramerateIndependentSmoother1d(0.2)

    @ClientOnly
    private var windSound: SimpleLoopingBlockEntitySoundInstance<WindTurbineBlockEntity>? = null

    @ClientOnly
    private var rotationSound: SimpleLoopingBlockEntitySoundInstance<WindTurbineBlockEntity>? = null

    override val clientSidePacketHandlerLazy = createClientSideHandler()

    override val delegateMap: MultiblockDelegateMap
        get() = (blockState.block as WindTurbineBlock).delegateMap

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(pLevel.isClientSide) {
            renderState = BasicKineticPart.RenderStateImpl()
        }
    }

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        handler.withHandler<RotatingKineticState>(RotatingKineticState::deserialize) {
            renderState?.load(it)
        }
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState ?: return

        if (windSound == null) {
            windSound = SimpleLoopingBlockEntitySoundInstance(this, Eln2Kinetic.WIND_TURBINE_WIND_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    angularVelocitySmoother.update(state.angularVelocity)
                    it.soundInfo = SoundInfo.wind(angularVelocitySmoother.value, WIND_TURBINE_REFERENCE_OMEGA)
                }

                it.registerOnAudioManager()
            }
        }

        if (rotationSound == null) {
            rotationSound = SimpleLoopingBlockEntitySoundInstance(this, Eln2Kinetic.WIND_TURBINE_ROTATION_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    it.soundInfo = SoundInfo.standardWithKineticScraping(state.angularVelocity, WIND_TURBINE_REFERENCE_OMEGA)
                }

                it.registerOnAudioManager()
            }
        }
    }

    @ServerOnly
    override fun onKineticStateChanged(state: RotatingKineticState) {
        sendBulkPacket(RotatingKineticState::serialize, state)
    }

    @ServerOnly
    override fun submitDisplay(builder: ComponentDisplayList) {
        val handle = cell.handle

        if(handle == null) {
            builder.debugInIDE { "NULL" }
        }
        else {
            builder.debugInIDE { "Factor: ${handle.clearanceFactor.rounded(4)}" }
            builder.debugInIDE { "Wind speed: ${handle.windSpeed.rounded(4)}" }
        }

        builder.quantity(cell.kinetic.node.angularVelocityQuantity)
        builder.quantityOutput(Quantity(cell.lastWindTorque, NEWTON_METER))
    }

    override fun setDestroyed() {
        destroyDelegates()
        super.setDestroyed()
    }
}

class WindTurbineBlockEntityVisual(
    ctx: VisualizationContext,
    blockEntity: WindTurbineBlockEntity,
    partialTick: Float
) : AbstractBlockEntityVisual<WindTurbineBlockEntity>(ctx, blockEntity, partialTick),
    ShaderLightVisual,
    SimpleDynamicVisual
{
    var version = 0
    val interpolator = KineticInterpolatorClient()

    val models = (blockState.block as WindTurbineBlock).model

    val base: TransformedInstance = ctx.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(models.baseModel, FlwMaterials.SMOOTH_LIT))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.translate(0.5f, 0.0f, 0.5f)
            it.rotateToFace(blockEntity.representativeFacing.clockWise)
        }

    val rotor: TransformedInstance = ctx.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(models.rotorMode, FlwMaterials.SMOOTH_LIT))
        .createInstance()

    private fun poseRotor() {
        rotor.setIdentityTransform()
        rotor.translate(visualPosition)
        rotor.translate(0.5f, 0.0f, 0.5f)
        rotor.rotateY(interpolator.clientRotation.ln().toFloat())
        rotor.rotateToFace(blockEntity.representativeFacing.clockWise)
        rotor.setChanged()
    }

    init {
        poseRotor()
    }

    override fun setSectionCollector(sectionCollector: SectionTrackedVisual.SectionCollector?) {
        this.lightSections = sectionCollector

        val block = blockState.block as WindTurbineBlock

        val set = LongOpenHashSet()
        set.add(SectionPos.asLong(blockEntity.representativePos))
        block.delegateMap.delegates.keys.forEach { delegatePosId ->
            val delegatePosWorld = MultiblockTransformations.transformMultiblockWorld(
                blockEntity.representativeFacing,
                blockEntity.representativePos,
                delegatePosId
            )

            set.add(SectionPos.asLong(delegatePosWorld))
        }

        lightSections.sections(set)
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        val renderState = blockEntity.renderState
            ?: return

        val targetVersion = renderState.version
        if(version != targetVersion) {
            version = targetVersion
            interpolator.applyServerState(renderState.angle, renderState.angularVelocity)
        }

        interpolator.update()
        poseRotor()
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(base)
        p0.accept(rotor)
    }

    override fun updateLight(p0: Float) {
        // NOOP
    }

    override fun _delete() {
        base.delete()
        rotor.delete()
    }
}
