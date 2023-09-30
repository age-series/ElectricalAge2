package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import it.unimi.dsi.fastutil.ints.*
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.GlassBlock
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.mc.*
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.applyBlockBenchTransform
import org.eln2.mc.client.render.foundation.colorLerp
import org.eln2.mc.common.blocks.foundation.GhostLightServer
import org.eln2.mc.common.blocks.foundation.GhostLightUpdateType
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.*
import org.eln2.mc.common.network.serverToClient.with
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.parts.foundation.PartRenderer
import org.eln2.mc.common.parts.foundation.PartUseInfo
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.mathematics.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*
import kotlin.random.Random

// Unfortunately, vanilla rendering has a problem which causes some smooth lighting to break
// Nothing I can do

private val originPositionLz = Vector3d(0.5, 0.5, 0.5)

/**
 * Traverses the light ray from [x], [y], [z] to the origin (0.5, 0.5, 0.5), and calls [user] with the voxel cell coordinates (minimal coordinates)
 * The origin is not included in the traversal.
 * */
private inline fun traverseLightRay(x: Int, y: Int, z: Int, crossinline user: (Int, Int, Int) -> Boolean) {
    if(x == 0 && y == 0 && z == 0) {
        error("Cannot traverse from origin to origin")
    }

    val sourcePositionLz = Vector3d(
        x + 0.5,
        y + 0.5,
        z + 0.5
    )

    val ray = Ray3d.fromSourceAndDestination(sourcePositionLz, originPositionLz)

    dda(ray, withSource = true) { i, j, k ->
        if (i == 0 && j == 0 && k == 0) {
            // Reached origin
            return@dda false
        }

        return@dda user(i, j, k)
    }
}

private inline fun traverseLightRay(pos: BlockPosInt, crossinline user: (Int, Int, Int) -> Boolean) {
    traverseLightRay(pos.x, pos.y, pos.z, user)
}

/**
 * Data storage for a *Light Field*. A light field is a regular grid of voxel cells with brightness values.
 *
 * It also implements a query for ray intersection, which returns the set of all cells whose ray traversals ([traverseLightRay]) intersect the query voxel.
 * */
interface LightFieldStorage {
    /**
     * Gets the light value at the specified [cell].
     * */
    fun getLight(cell: BlockPosInt) : Byte

    /**
     * Gets all cells whose ray to the origin intersects the [voxel].
     * */
    fun intersectRays(voxel: BlockPosInt) : Iterable<Int>
}

/**
 * *Baked* (pre-computed) [LightFieldStorage].
 * All queries are pre-computed and stored in hash tables. It is very expensive in memory! For testing only.
 * @param sourceBrightnessField The source brightness field. The data is cloned to ensure immutability of this [BakedLightFieldStorage].
 * */
class BakedLightFieldStorage(sourceBrightnessField: Map<Int, Byte>) : LightFieldStorage {
    // Defensive copy to be absolutely sure it is immutable.
    private val brightnessField = Int2ByteOpenHashMap(sourceBrightnessField)
    private val intersections = Int2ObjectOpenHashMap<ImmutableIntArrayView>()

    init {
        val watch = Stopwatch()
        val intersectionsBuffer = Int2ObjectOpenHashMap<IntArrayList>(brightnessField.size)

        var fieldIndex = 0
        var cellsPerSecond = 0

        brightnessField.forEach { (sourcePositionInt, _) ->
            ++fieldIndex

            val x = BlockPosInt.unpackX(sourcePositionInt)
            val y = BlockPosInt.unpackY(sourcePositionInt)
            val z = BlockPosInt.unpackZ(sourcePositionInt)

            if(x == 0 && y == 0 && z == 0) {
                // Do not trace from origin
                return@forEach
            }

            traverseLightRay(x, y, z) { i, j, k ->
                val key = BlockPosInt.pack(i, j, k)
                var list = intersectionsBuffer.get(key)

                if(list == null) {
                    list = IntArrayList()
                    intersectionsBuffer.put(key, list)
                }

                list.add(sourcePositionInt)

                ++cellsPerSecond

                return@traverseLightRay true
            }

            if(watch.total >= 0.25) {
                LOG.warn("Baking... $fieldIndex/${brightnessField.size}, ${(cellsPerSecond.toDouble() / !watch.total).formatted()} R/S")
                cellsPerSecond = 0
                watch.resetTotal()
            }
        }

        var intCount = 0L

        intersectionsBuffer.keys.forEach { voxelInt ->
            val list = intersectionsBuffer[voxelInt]
            intCount += list.size
            intersections[voxelInt] = ImmutableIntArrayView(list.toIntArray())
        }

        LOG.warn("Intersection overhead: $intCount")

        OVERHEAD_TOTAL.addAndGet(intCount)
    }

    fun getAsMap() : Map<Int, Byte> = brightnessField

    override fun getLight(cell: BlockPosInt): Byte {
        val result = brightnessField.getOrDefault(!cell, -1)
        require(result.compareTo(-1) != 0) { "Queried light value outside of light field" }
        return result
    }

    override fun intersectRays(voxel: BlockPosInt) = intersections.get(!voxel) ?: QUERY_EMPTY

    companion object {
        private val QUERY_EMPTY = ImmutableIntArrayView(IntArray(0))
        val OVERHEAD_TOTAL = AtomicLong()
    }
}

/**
 * [LightFieldStorage] with a pruned search algorithm for ray intersections.
 * Slower than baking, but without a large memory overhead. It is still very fast in testing.
 * Algorithm steps:
 *  - Start a flood fill at the query voxel (certainly, the traversal from the query voxel intersects the query voxel)
 *  - Flood towards neighbors whose rays intersect the bounding box of the query voxel (AABB-Ray intersection)
 *
 * This algorithm **will not work** if there are gaps in the light field, which could cause the search to end early.
 * */
class HomogenousLightFieldStorage(sourceBrightnessField: Map<Int, Byte>) : LightFieldStorage {
    // Defensive copy to be absolutely sure it is immutable.
    private val brightnessField = Int2ByteOpenHashMap(sourceBrightnessField)

    fun getAsMap(): Map<Int, Byte> = brightnessField

    override fun getLight(cell: BlockPosInt): Byte {
        val result = brightnessField.getOrDefault(!cell, -1)
        require(result.compareTo(-1) != 0) { "Queried light value outside of light field" }
        return result
    }

    override fun intersectRays(voxel: BlockPosInt): Iterable<Int> {
        val minX = voxel.x.toDouble()
        val minY = voxel.y.toDouble()
        val minZ = voxel.z.toDouble()
        val maxX = minX + 1.0
        val maxY = minY + 1.0
        val maxZ = minZ + 1.0

        val queue = IntArrayFIFOQueue()
        val results = IntOpenHashSet()

        queue.enqueue(!voxel)

        while (!queue.isEmpty) {
            val front = queue.dequeueInt()

            if(!results.add(front) || brightnessField[front] < 1) {
                continue
            }

            val x = BlockPosInt.unpackX(front)
            val y = BlockPosInt.unpackY(front)
            val z = BlockPosInt.unpackZ(front)

            for (i in 0..5) {
                val step = STEPS[i]

                val nx = x + step.stepX
                val ny = y + step.stepY
                val nz = z + step.stepZ

                val cx = nx + 0.5
                val cy = ny + 0.5
                val cz = nz + 0.5

                val k = -1.0 / sqrt((nx * nx + ny * ny + nz * nz).toDouble())

                val dx = 1.0 / (nx * k)
                val dy = 1.0 / (ny * k)
                val dz = 1.0 / (nz * k)

                val a  = (minX - cx) * dx
                val b  = (maxX - cx) * dx
                val c  = (minY - cy) * dy
                val d  = (maxY - cy) * dy
                val e  = (minZ - cz) * dz
                val f  = (maxZ - cz) * dz

                val tMin = max(max(min(a, b), min(c, d)), min(e, f))
                val tMax = min(min(max(a, b), max(c, d)), max(e, f))

                if(tMax >= 0.0 && (tMax - tMin) > SNZE_EPSILONf) {
                    queue.enqueue(BlockPosInt.pack(nx, ny, nz))
                }
            }
        }

        return results
    }

    companion object {
        private val STEPS = Direction.values()
    }
}

/**
 * Computes the state transitions from every state to every other state.
 * @param states The fields, parameterized by state increment.
 * @return The map of all transitions.
 * */
private fun computeStateTransitions(states: Map<Int, Map<Int, Byte>>) : Long2ObjectOpenHashMap<LightVolumeTransition> {
    require(states.isNotEmpty()) { "Cannot compute state transitions for 0 states " }

    val watch = Stopwatch()
    val results = Long2ObjectOpenHashMap<LightVolumeTransition>()
    var pairIndex = 0

    for (incr1 in states.keys) {
        val brightnesses1 = states[incr1]!!

        val removed = IntArrayList()
        val updated = IntArrayList()

        for (incr2 in states.keys) {
            if(incr1 == incr2) {
                continue
            }

            ++pairIndex

            val brightnesses2 = Int2ByteOpenHashMap(states[incr2]!!)

            removed.clear()
            updated.clear()

            // Computes deleted and updated:
            brightnesses1.forEach { (position1Int, brightness1) ->
                require(brightness1 > 0) { "Light field 1 in g1 pass has $brightness1 cell" }

                val brightness2 = brightnesses2.remove(position1Int)

                if(brightness2 < 1) {
                    // No longer in set 2, so deleted:
                    removed.add(position1Int)
                }
                else {
                    // In set 1 and set 2, so update:
                    updated.add(position1Int)
                }
            }

            val inserted = IntArray(brightnesses2.size)
            var insertedI = 0

            // Computes inserted:
            brightnesses2.forEach { (position2Int, brightness2) ->
                require(brightness2 > 0) { "Light field in g2 pass has 0 cell" }
                inserted[insertedI++] = position2Int
            }

            require(insertedI == inserted.size)

            results.put(
                !IntPair(incr1, incr2),
                LightVolumeTransition(
                    ImmutableIntArrayView(inserted),
                    ImmutableIntArrayView(removed.toIntArray()),
                    ImmutableIntArrayView(updated.toIntArray())
                )
            )

            if(watch.total > 0.5) {
                LOG.warn("Computing transitions $pairIndex/${(states.size - 1) * (states.size - 1)}")
                watch.resetTotal()
            }
        }
    }

    return results
}

/**
 * Implements a [LightVolumeProvider] parameterized by face.
 * It provides one light volume per orientation of the emitter; presumably, the light volume itself is an oriented shape.
 * @param variantsByFace The variants (map of state increment to the grid of brightnesses) for every face.
 * */
class FaceOrientedLightVolumeProvider(variantsByFace: Map<FaceLocator, Map<Int, Map<Int, Byte>>>) : LightVolumeProvider {
    init {
        require(variantsByFace.isNotEmpty()) { "Cannot create face oriented provider with empty variants" }
        require(variantsByFace.values.all { it.containsKey(0) }) { "Some supplied variants by face did not have a configuration at state 0" }
    }

    private val volumesByFace = variantsByFace.mapValues { (_, variantsByState) ->
        val transitions = computeStateTransitions(variantsByState)

        val storage = variantsByState.mapValues { (_, field) ->
            HomogenousLightFieldStorage(field)
        }

        Volume(storage, transitions)
    }

    override fun getVolume(locatorSet: LocatorSet): LightVolume {
        val face = locatorSet.requireLocator<FaceLocator> {
            "Face-oriented lights require a face locator"
        }

        return volumesByFace[face] ?: error("Oriented light volume did not have $face")
    }

    private class Volume(val variantsByState: Map<Int, LightFieldStorage>, val transitions: Long2ObjectOpenHashMap<LightVolumeTransition>) : LightVolume {
        override val stateIncrements: Int
            get() = variantsByState.size - 1

        override fun getLightField(state: Int) = variantsByState[state]!!

        override fun getVolumeTransition(actualState: Int, targetState: Int) = transitions.get(!IntPair(actualState, targetState))
            ?: error("Did not have transition from $actualState to $targetState")
    }
}

object LightFieldPrimitives {
    /**
     * Computes a cone light.
     * @param increments The number of state increments to use
     * @param strength The light range and intensity
     * @param deviationMax The maximum angle between the surface normal and a light ray
     * */
    fun cone(increments: Int, strength: Double, deviationMax: Double): FaceOrientedLightVolumeProvider {
        val variantsByFace = HashMap<FaceLocator, HashMap<Int, Int2ByteOpenHashMap>>()
        val cosDeviationMax = cos(deviationMax)

        var currentStep = 0

        val sw = Stopwatch()
        val directions = Direction.values()

        Direction.values().forEach { face ->
            val normal = face.toVector3d()

            val variants = HashMap<Int, Int2ByteOpenHashMap>()
            variantsByFace[face] = variants

            for (state in 0 .. increments) {
                ++currentStep

                if(sw.total > 0.5) {
                    sw.resetTotal()
                    LOG.warn("Computing increment $currentStep/${6 * (increments + 1)}")
                }

                val grid = Int2ByteOpenHashMap()
                variants[state] = grid

                val lightRadiusBase = strength * (state / increments.toDouble())
                val lightRadiusUpper = ceil(lightRadiusBase).toInt()

                if(lightRadiusUpper == 0) {
                    continue
                }

                fun set(x: Int, y: Int, z: Int) {
                    val t = Vector3d(x, y, z)

                    val distance = t.norm

                    if(!distance.approxEq(0.0)) {
                        if(((t / distance) cosAngle normal) < cosDeviationMax) {
                            return
                        }
                    }

                    if(distance <= lightRadiusUpper) {
                        val unitDistance = distance / lightRadiusBase
                        val intensity = 1.0 - unitDistance

                        val brightness = round(strength * intensity).toInt().coerceIn(0, 15)

                        if(brightness > 0) {
                            grid.put(BlockPosInt.pack(x, y, z), brightness.toByte())
                        }
                    }
                }

                when(face) {
                    Direction.DOWN -> {
                        for (x in -lightRadiusUpper..lightRadiusUpper) {
                            for (y in -lightRadiusUpper..0) {
                                for (z in -lightRadiusUpper..lightRadiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.UP -> {
                        for (x in -lightRadiusUpper..lightRadiusUpper) {
                            for (y in 0..lightRadiusUpper) {
                                for (z in -lightRadiusUpper..lightRadiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.NORTH -> {
                        for (x in -lightRadiusUpper..lightRadiusUpper) {
                            for (y in -lightRadiusUpper..lightRadiusUpper) {
                                for (z in -lightRadiusUpper..0) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.SOUTH -> {
                        for (x in -lightRadiusUpper..lightRadiusUpper) {
                            for (y in -lightRadiusUpper..lightRadiusUpper) {
                                for (z in 0..lightRadiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.WEST -> {
                        for (x in -lightRadiusUpper..0) {
                            for (y in -lightRadiusUpper..lightRadiusUpper) {
                                for (z in -lightRadiusUpper..lightRadiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.EAST -> {
                        for (x in 0..lightRadiusUpper) {
                            for (y in -lightRadiusUpper..lightRadiusUpper) {
                                for (z in -lightRadiusUpper..lightRadiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                }

                // Ameliorate the smooth lighting problem a bit by decreasing some values at the frontiers.

                for (kvp in grid) {
                    val centerPos = BlockPosInt(kvp.key)
                    val x = centerPos.x
                    val y = centerPos.y
                    val z = centerPos.z

                    if((x == 0 && y == 0 && z == 0)) {
                        continue
                    }

                    require(kvp.value > 0)

                    var isFrontier = false

                    for (direction in directions) {
                        val neighbor = grid.get(
                            BlockPosInt.pack(
                                x + direction.stepX,
                                y + direction.stepY,
                                z + direction.stepZ
                            )
                        )

                        if(neighbor < 1) {
                            isFrontier = true
                            break
                        }
                    }

                    if(isFrontier) {
                        grid.replace(kvp.key, max(1, (round(kvp.value / 2.0).toInt())).toByte())
                    }
                }
            }
        }

        return FaceOrientedLightVolumeProvider(variantsByFace)
    }
}

/**
 * Represents the state of a light emitter.
 * */
interface LightView {
    /**
     * Gets the (raw) electrical power. This may be negative, depending on polarity.
     * */
    val power: Double
    /**
     * Gets the (raw) electrical current. This may be negative, depending on polarity.
     * */
    val current: Double
    /**
     * Gets the (raw) electrical potential across the resistor. This may be negative, depending on polarity.
     * */
    val potential: Double
    /**
     * Gets the life parameter (0-1).
     * */
    val life: Double
    /**
     * Gets the latest model temperature, obtained from the [LightTemperatureFunction].
     * */
    val modelTemperature: Double
    /**
     * Gets the latest state incremement.
     * */
    val volumeState: Int
}

/**
 * Computes the light "temperature". This equates to how strong the light is, based on emitter state (e.g. based on power input)
 * */
fun interface LightTemperatureFunction {
    fun computeTemperature(view: LightView): Double
}

/**
 * Computes a damage increment, based on emitter state.
 * */
fun interface LightDamageFunction {
    fun computeDamage(view: LightView, dt: Double): Double
}

/**
 * Computes resistance, based on emitter state.
 * */
fun interface LightResistanceFunction {
    fun computeResistance(view: LightView): Quantity<Resistance>
}

/**
 * Describes the transition of a light volume, from one state to another.
 * @param inserted The list of cells that, initially, were not included in the light field (their brightness is 0), but now have been inserted (their brightness is larger than 0)
 * @param removed The list of cells that, initially, were lit with a non-zero brightness, but now have been removed (their brightness is 0)
 * @param updated The list of cells that, initially, were lit with a non-zero brightness, but now have been re-lit with another non-zero brightness.
 * */
class LightVolumeTransition(
    val inserted: ImmutableIntArrayView,
    val removed: ImmutableIntArrayView,
    val updated: ImmutableIntArrayView,
)

/**
 * Provider for a [LightVolume], parameterized on the spatial configuration of the light emitter.
 * */
fun interface LightVolumeProvider {
    fun getVolume(locatorSet: LocatorSet) : LightVolume
}

/**
 * *Light Volume* (light voxel data) function. This function maps a state increment (light "brightness") to the
 * desired light voxels. It also provides information about the change in state between 2 state increments ([LightVolumeTransition]).
 * */
interface LightVolume {
    /**
     * Gets the number of "state increments" (number of variants of the light field when the emitter is **powered**)
     * The *state* is given by the [LightTemperatureFunction] (whose co-domain is 0-1).
     * The temperature gets mapped to a discrete state (represented as an integer), and will be in the range [0, [stateIncrements]].
     * This means *[stateIncrements] + 1* states should be handled, the state "0" corresponding to the light when temperature is 0.
     * */
    val stateIncrements: Int

    /**
     * Gets the desired light field, based on the current state of the light source, as per [view].
     * */
    fun getLightField(state: Int) : LightFieldStorage

    /**
     * Gets the [LightVolumeTransition] when going from the state [actualState] to the state [targetState].
     * */
    fun getVolumeTransition(actualState: Int, targetState: Int): LightVolumeTransition
}

/**
 * Describes the behavior of a light source.
 * */
data class LightModel(
    val temperatureFunction: LightTemperatureFunction,
    val resistanceFunction: LightResistanceFunction,
    val damageFunction: LightDamageFunction,
    val volumeProvider: LightVolumeProvider,
)

/**
 * Event sent when the state increment changes.
 * @param volume The current volume in use.
 * @param targetState The new state increment.
 * */
data class VolumetricLightChangeEvent(val volume: LightVolume, val targetState: Int) : Event

/**
 * Event sent when the life parameter reaches 0.
 * */
object LightBurnedOutEvent : Event

/**
 * Consumer for the raw temperature of the light.
 * The results are presumably sent over the network.
 * */
fun interface LightTemperatureConsumer {
    fun consume(temperature: Double)
}

class LightCell(ci: CellCreateInfo, poleMap: PoleMap) : Cell(ci), DataEntity, WailaEntity, LightView {
    companion object {
        private const val ID = "id"
        private const val LIFE = "life"
        private const val RENDER_EPS = 1e-4
        private const val DAMAGE_EPS = 1e-4
        private const val OPEN_CIRCUIT_RESISTANCE = LARGE_RESISTANCE
        private const val RESISTANCE_EPS = 0.1
    }

    init {
        data.withField(TooltipField { b ->
            b.text("Minecraft Brightness", volumeState)
            b.text("Model Brightness", modelTemperature.formatted())

            val item = this.item

            if(item == null) {
                b.text("Bulb", "N/A")
            }
            else {
                b.text("Bulb", ForgeRegistries.ITEMS.getKey(item))
                b.text("Life", life.formattedPercentN())
            }
        })
    }

    // The last render brightness sent:
    private var trackedRenderBrightness: Double = 0.0

    // Accessor to send the render brightness:
    private var renderBrightnessConsumer: LightTemperatureConsumer? = null

    // An event queue hooked into the game object:
    private var serverThreadReceiver: EventQueue? = null

    @SimObject @Inspect
    val resistor = ResistorObject(this, poleMap).also {
        it.resistanceExact = OPEN_CIRCUIT_RESISTANCE
    }

    @SimObject @Inspect
    val thermalWire = ThermalWireObject(this)

    @Behavior
    val explosion = TemperatureExplosionBehavior(
        { thermalWire.thermalBody.temperatureKelvin },
        TemperatureExplosionBehaviorOptions(),
        this
    )

    override var volumeState: Int = 0

    override var modelTemperature = 0.0
        private set

    override val power: Double get() = resistor.power
    override val current: Double get() = resistor.current
    override val potential: Double get() = resistor.potential

    override var life: Double = 0.0

    var item: LightBulbItem? = null
    var volume: LightVolume? = null

    fun resetValues() {
        resistor.updateResistance(OPEN_CIRCUIT_RESISTANCE)
        modelTemperature = 0.0
        trackedRenderBrightness = 0.0
        volumeState = 0
        life = 0.0
        item = null
        volume = null
    }

    fun bind(serverThreadAccess: EventQueue, renderBrightnessConsumer: LightTemperatureConsumer) {
        this.serverThreadReceiver = serverThreadAccess
        this.renderBrightnessConsumer = renderBrightnessConsumer
    }

    fun unbind() {
        serverThreadReceiver = null
        renderBrightnessConsumer = null
    }

    override fun subscribe(subs: SubscriberCollection) {
        subs.addPre10(this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        val lightModel = this.item?.model

        if(lightModel == null || life approxEq 0.0) {
            return
        }

        // Fetch volume if not fetched:
        volume = volume ?: lightModel.volumeProvider.getVolume(locator)
        val volume = volume!!

        val gameEventReceiver = this.serverThreadReceiver ?: return

        // Tick down consumption:
        val damage = lightModel.damageFunction.computeDamage(this, dt).absoluteValue

        if(damage > 0.0) {
            life = (life - damage).coerceIn(0.0, 1.0)
            setChanged()
        }

        if(life approxEq 0.0) {
            life = 0.0
            // Light has burned out:
            gameEventReceiver.enqueue(LightBurnedOutEvent)
            resetValues()
            setChanged()
            return
        }

        // Evaluate temperature:
        modelTemperature = lightModel.temperatureFunction.computeTemperature(this).coerceIn(0.0, 1.0)

        // Update power consumption:
        resistor.updateResistance(!lightModel.resistanceFunction.computeResistance(this), RESISTANCE_EPS)

        // Send new value to client:
        if (!modelTemperature.approxEq(trackedRenderBrightness, RENDER_EPS)) {
            trackedRenderBrightness = modelTemperature
            renderBrightnessConsumer?.consume(modelTemperature)
        }

        // Find target state based on temperature:s
        val targetState = round(modelTemperature * volume.stateIncrements).toInt().coerceIn(0, volume.stateIncrements)

        // Detect changes:
        if (volumeState != targetState) {
            volumeState = targetState
            gameEventReceiver.enqueue(VolumetricLightChangeEvent(volume, targetState))
        }
    }

    override fun saveCellData(): CompoundTag {
        val tag = CompoundTag()

        item?.also { item ->
            ForgeRegistries.ITEMS.getKey(item)?.also { itemId ->
                tag.putResourceLocation(ID, itemId)
                tag.putDouble(LIFE, life)
            }
        }

        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        if(tag.contains(ID)) {
            ForgeRegistries.ITEMS.getValue(tag.getResourceLocation(ID))?.also {
                if(it is LightBulbItem) {
                    item = it
                    life = tag.getDouble(LIFE)
                }
            }
        }
    }
}

class LightBulbItem(val model: LightModel) : Item(Properties()) {
    companion object {
        private const val LIFE = "life"
    }

    fun createStack(life: Double, count: Int = 1): ItemStack {
        val stack = ItemStack(this, count)

        stack.tag = CompoundTag().also {
            it.putDouble(LIFE, life)
        }

        return stack
    }

    fun getLife(stack: ItemStack): Double {
        return stack.tag?.getDouble(LIFE) ?: 0.0
    }

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        val life = getLife(pStack)

        pTooltipComponents.add(Component.literal("Life: ${life.formattedPercentN()}"))
    }
}

class LightPart(id: ResourceLocation, placementContext: PartPlacementInfo, cellProvider: CellProvider<LightCell>) : CellPart<LightCell, LightRenderer>(id, placementContext, cellProvider), EventListener {
    override val partSize = bbVec(8.0, 1.0 + 2.302, 5.0)

    // We are working in the volume frame, and our part is the origin:
    private fun getPositionWorld(positionVolume: BlockPosInt) = BlockPos(
        placement.position.x + positionVolume.x,
        placement.position.y + positionVolume.y,
        placement.position.z + positionVolume.z
    )

    private fun getPositionWorld(x: Int, y: Int, z: Int) = BlockPos(
        placement.position.x + x,
        placement.position.y + y,
        placement.position.z + z
    )

    /**
     * Checks if the block at [position] can transmit a ray.
     * @param position The position to check, in the world frame.
     * @return True, if the block at the specified [position] can transmit the ray. Otherwise, false.
     * */
    private fun transmitsRayWorld(position: BlockPos): Boolean {
        requireIsOnServerThread()

        val state = placement.level.getBlockState(position)

        if(state.isAir) {
            return true
        }

        if(state.block is GlassBlock) {
            return true
        }

        if(!state.isCollisionShapeFullBlock(placement.level, position)) {
            return true
        }

        return false
    }

    private val lightCells = HashMap<Int, LightVoxel>()
    private var volume: LightVolume? = null
    private var state: Int = 0

    private fun resetValues() {
        volume = null
        state = 0
     }

    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if(placement.level.isClientSide || context.hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS
        }

        val level = placement.level as ServerLevel
        var result = InteractionResult.FAIL

        cell.graph.runSuspended {
            val existingItem = cell.item

            if(existingItem != null) {
                destroyCells()

                level.addItem(placement.position, existingItem.createStack(cell.life))
                cell.resetValues()
                sendClientBrightness(0.0)
                resetValues()

                result = InteractionResult.SUCCESS
            }
            else {
                val stack = context.player.mainHandItem

                if(stack.count > 0 && stack.item is LightBulbItem) {
                    destroyCells()

                    val item = stack.item as LightBulbItem
                    cell.resetValues()
                    cell.life = item.getLife(stack)
                    cell.item = item
                    resetValues()

                    result = InteractionResult.CONSUME
                }
            }
        }

        return result
    }

    override fun createRenderer() = LightRenderer(
        this,
        PartialModels.SMALL_WALL_LAMP_CAGE,
        PartialModels.SMALL_WALL_LAMP_EMITTER
    ).also { it.downOffset = partSize.y / 2.0 }

    @ServerOnly @OnServerThread
    override fun onCellAcquired() {
        val events = Scheduler.register(this)

        events.registerHandler(this::onVolumeUpdated)
        events.registerHandler(this::onLightBurnedOut)

        cell.bind(
            serverThreadAccess = Scheduler.getEventAccess(this),
            renderBrightnessConsumer = ::sendClientBrightness
        )
    }

    @ServerOnly
    private fun sendClientBrightness(value: Double) {
        val buffer = ByteBuffer.allocate(8) with value
        enqueueBulkMessage(buffer.array())
    }

    @ClientOnly
    override fun handleBulkMessage(msg: ByteArray) {
        val buffer = ByteBuffer.wrap(msg)
        renderer.updateBrightness(buffer.double)
    }

    @ServerOnly @OnServerThread
    private fun onVolumeUpdated(event: VolumetricLightChangeEvent) {
        requireIsOnServerThread()

        if(volume == null) {
            volume = event.volume
        }

        require(this.volume == event.volume)

        if(event.targetState == state) {
            LOG.error("Same state")
            return
        }

        val volume = this.volume!!

        val transition = volume.getVolumeTransition(state, event.targetState)
        val targetField = volume.getLightField(event.targetState)

        for (i in transition.inserted.indices) {
            val posVolume = BlockPosInt(transition.inserted[i])
            val posWorld = getPositionWorld(posVolume)

            if(!GhostLightServer.canCreateHandle(placement.level, posWorld)) {
                continue
            }

            val cell = lightCells.computeIfAbsent(!posVolume) {
                LightVoxel(posVolume, posWorld)
            }

            cell.updateBrightness(targetField.getLight(posVolume).toInt())
        }

        for (i in transition.updated.indices) {
            val posVolume = BlockPosInt(transition.updated[i])
            val cell = lightCells[!posVolume] ?: continue // Out of bounds?
            cell.updateBrightness(targetField.getLight(posVolume).toInt())
        }

        for (i in transition.removed.indices) {
            val posVolume = BlockPosInt(transition.removed[i])
            val cell = lightCells[!posVolume] ?: continue // Out of bounds?
            cell.updateBrightness(0)
        }

        this.state = event.targetState
    }

    @ServerOnly @OnServerThread
    private fun destroyCells() {
        requireIsOnServerThread()

        for (it in lightCells.values) {
            it.destroy()
        }

        lightCells.clear()
    }

    @ServerOnly @OnServerThread
    private fun onLightBurnedOut(event: LightBurnedOutEvent) {
        sendClientBrightness(0.0)
        destroyCells()
        placement.level.playLocalSound(
            placement.position.x.toDouble(),
            placement.position.y.toDouble(),
            placement.position.z.toDouble(),
            SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS,
            1.0f,
            Random.nextDouble(0.9, 1.1).toFloat(),
            false
        )
    }

    @ServerOnly @OnServerThread
    override fun onSyncSuggested() {
        sendClientBrightness(cell.modelTemperature)
    }

    override fun onCellReleased() {
        cell.unbind()
        Scheduler.remove(this)
        destroyCells()
    }

    override fun onRemoved() {
        super.onRemoved()
        destroyCells()
    }

    private inner class LightVoxel(val voxelPositionVolume: BlockPosInt, voxelPositionWorld: BlockPos) {
        private val handle = GhostLightServer.createHandle(placement.level, voxelPositionWorld) { _, type ->
            onUpdate(type)
        }!!

        /**
         * Set of all voxel cells that are obstructing the ray.
         * These positions are in the volume frame.
         * */
        private val obstructingBlocks = IntOpenHashSet()
        private val isObstructed get() = obstructingBlocks.size > 0
        private val isOrigin get() = voxelPositionVolume.isZero

        init {
            // Initially, we intersect all blocks in the world, to find the blocks occluding the ray.
            // Then, we'll keep our state updated on events.

            if(!isOrigin) {
                traverseLightRay(voxelPositionVolume) { x, y, z ->
                    if(!transmitsRayWorld(getPositionWorld(x, y, z))) {
                        // But we store in the volume frame as 1 int, it is way more efficient:
                        obstructingBlocks.add(BlockPosInt.pack(x, y, z))
                    }

                    true
                }
            }
        }

        private var desiredBrightness = 0

        fun updateBrightness(brightness: Int) {
            if(desiredBrightness == brightness) {
                return
            }

            desiredBrightness = brightness

            if(!isObstructed) {
                // Immediately set light, because we are not obstructed:
                handle.setBrightness(brightness)
            }
        }

        private fun onUpdate(type: GhostLightUpdateType) {
            if(isOrigin) {
                // Weird to get updates here, ignore.
                LOG.warn("Got light update at origin")
                return
            }

            // If this block is not an obstruction, it doesn't affect us:
            if(type == GhostLightUpdateType.Closed && transmitsRayWorld(handle.position)) {
                return
            }

            // First of all, we'll get the light cells whose rays intersect this voxel. That includes this one too!

            val field = volume!!.getLightField(state)
            val intersectionsVolume = field.intersectRays(voxelPositionVolume)

            when(type) {
                GhostLightUpdateType.Closed -> {
                    // Add to obstructions list and update:

                    for (k in intersectionsVolume) {
                        val cell = lightCells[k] ?: continue
                        cell.obstructingBlocks.add(!voxelPositionVolume)
                        // If we added one, it automatically means the cell is blocked:
                        cell.handle.setBrightness(0)
                    }
                }
                GhostLightUpdateType.Opened -> {
                    // Remove from list of obstructions and, if empty, set light:
                    for (k in intersectionsVolume) {
                        val cell = lightCells[k] ?: continue

                        if(cell.obstructingBlocks.remove(!voxelPositionVolume)) {
                            if(!cell.isObstructed) {
                                cell.handle.setBrightness(cell.desiredBrightness)
                            }
                        }
                        // It should always remove. But I think it is harsh to crash the game if we don't, we'll log instead:
                        else {
                            LOG.error("Failed to remove obstruction $voxelPositionVolume")
                        }
                    }
                }
            }
        }

        fun destroy() {
            handle.destroy()
        }
    }
}

class LightRenderer(private val part: LightPart, private val cage: PartialModel, private val emitter: PartialModel) : PartRenderer() {
    companion object {
        val COLD_TINT = Color(255, 255, 255, 255)
        val WARM_TINT = Color(254, 196, 127, 255)
    }

    private val brightnessUpdate = AtomicUpdate<Double>()
    private var brightness = 0.0

    fun updateBrightness(newValue: Double) = brightnessUpdate.setLatest(newValue)

    var yRotation = 0f
    var downOffset = 0.0

    private var cageInstance: ModelData? = null
    private var emitterInstance: ModelData? = null

    override fun setupRendering() {
        buildInstance()
    }

    private fun create(model: PartialModel): ModelData {
        return multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .applyBlockBenchTransform(part, downOffset, yRotation)
    }

    private fun buildInstance() {
        cageInstance?.delete()
        emitterInstance?.delete()
        cageInstance = create(cage)
        emitterInstance = create(emitter)
        multipart.relightPart(part)
        tint()
        relight()
    }

    override fun getModelsToRelight(): List<FlatLit<*>> {
        val list = ArrayList<ModelData>(2)

        cageInstance?.also { list.add(it) }
        emitterInstance?.also { list.add(it) }

        return list
    }

    override fun remove() {
        cageInstance?.delete()
        emitterInstance?.delete()
    }

    private fun tint() {
        emitterInstance!!.setColor(colorLerp(COLD_TINT, WARM_TINT, brightness.toFloat()))
    }

    private fun relight() {
        multipart.relightPart(part)
    }

    override fun beginFrame() {
        val emitter = emitterInstance ?: return

        brightnessUpdate.consume {
            brightness = it.coerceIn(0.0, 1.0)
            tint()
            relight()
        }
    }
}
