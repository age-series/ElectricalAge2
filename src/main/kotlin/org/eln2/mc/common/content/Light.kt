package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import it.unimi.dsi.fastutil.ints.*
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
import org.ageseries.libage.data.Quantity
import org.eln2.mc.*
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.transformPart
import org.eln2.mc.client.render.foundation.colorLerp
import org.eln2.mc.common.blocks.foundation.GhostLightServer
import org.eln2.mc.common.blocks.foundation.GhostLightUpdateType
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.*
import org.eln2.mc.common.network.serverToClient.with
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.mathematics.*
import java.nio.ByteBuffer
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

private inline fun traverseLightRay(pos: Int, crossinline user: (Int, Int, Int) -> Boolean) {
    traverseLightRay(BlockPosInt.unpackX(pos), BlockPosInt.unpackY(pos), BlockPosInt.unpackZ(pos), user)
}

private inline fun rayIntersectionAnalyzer(voxel: Int, crossinline predicate: (Int) -> Boolean) : IntOpenHashSet {
    val minX = BlockPosInt.unpackX(voxel).toDouble()
    val minY = BlockPosInt.unpackY(voxel).toDouble()
    val minZ = BlockPosInt.unpackZ(voxel).toDouble()
    val maxX = minX + 1.0
    val maxY = minY + 1.0
    val maxZ = minZ + 1.0

    val queue = IntArrayFIFOQueue()
    val results = IntOpenHashSet()

    queue.enqueue(voxel)

    while (!queue.isEmpty) {
        val front = queue.dequeueInt()

        if (!results.add(front) || !predicate(front)) {
            continue
        }

        val x = BlockPosInt.unpackX(front)
        val y = BlockPosInt.unpackY(front)
        val z = BlockPosInt.unpackZ(front)

        for (i in 0..5) {
            val nx = x + directionIncrementX(i)
            val ny = y + directionIncrementY(i)
            val nz = z + directionIncrementZ(i)

            val cx = nx + 0.5
            val cy = ny + 0.5
            val cz = nz + 0.5

            val k = -1.0 / sqrt((nx * nx + ny * ny + nz * nz).toDouble())

            val dx = 1.0 / (nx * k)
            val dy = 1.0 / (ny * k)
            val dz = 1.0 / (nz * k)

            val a = (minX - cx) * dx
            val b = (maxX - cx) * dx
            val c = (minY - cy) * dy
            val d = (maxY - cy) * dy
            val e = (minZ - cz) * dz
            val f = (maxZ - cz) * dz

            val tMin = max(max(min(a, b), min(c, d)), min(e, f))
            val tMax = min(min(max(a, b), max(c, d)), max(e, f))

            if (tMax >= 0.0 && (tMax - tMin) > 0.0) {
                queue.enqueue(BlockPosInt.pack(nx, ny, nz))
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
        LightVolume(variantsByState)
    }

    override fun getVolume(locatorSet: Locator): LightVolume {
        val face = locatorSet.requireLocator<FaceLocator> {
            "Face-oriented lights require a face locator"
        }

        return volumesByFace[face] ?: error("Oriented light volume did not have $face")
    }
}

object LightFieldPrimitives {
    /**
     * Computes a cone light.
     * @param increments The number of state increments to use
     * @param strength The light range and intensity
     * @param deviationMax The maximum angle between the surface normal and a light ray
     * */
    fun cone(increments: Int, strength: Double, deviationMax: Double, baseRadius: Int): FaceOrientedLightVolumeProvider {
        val variantsByFace = HashMap<FaceLocator, HashMap<Int, Int2ByteOpenHashMap>>()
        val cosDeviationMax = cos(deviationMax)

        var currentStep = 0

        Direction.values().forEach { face ->
            val baseVectors = let {
                val results = ArrayList<Vector3d>()

                if(baseRadius <= 0) {
                    results.add(Vector3d.zero)
                }
                else {
                    when(face.axis) {
                        Direction.Axis.X -> {
                            for (y in -baseRadius..baseRadius) {
                                for (z in -baseRadius..baseRadius) {
                                    results.add(Vector3d(0.0, y.toDouble(), z.toDouble()))
                                }
                            }
                        }
                        Direction.Axis.Y -> {
                            for (x in -baseRadius..baseRadius) {
                                for (z in -baseRadius..baseRadius) {
                                    results.add(Vector3d(x.toDouble(), 0.0, z.toDouble()))
                                }
                            }
                        }
                        Direction.Axis.Z -> {
                            for (x in -baseRadius..baseRadius) {
                                for (y in -baseRadius..baseRadius) {
                                    results.add(Vector3d(x.toDouble(), y.toDouble(), 0.0))
                                }
                            }
                        }
                        else -> error("Invalid axis ${face.axis}")
                    }
                }

                results
            }

            val normal = face.toVector3d()

            val variants = HashMap<Int, Int2ByteOpenHashMap>()
            variantsByFace[face] = variants

            for (state in 0 .. increments) {
                ++currentStep

                val fieldBase = Int2DoubleOpenHashMap()
                val results = Int2ByteOpenHashMap(fieldBase.size)
                variants[state] = results

                val radius = strength * (state / increments.toDouble())
                val radiusUpper = ceil(radius).toInt()

                if(radiusUpper == 0) {
                    continue
                }

                fun set(x: Int, y: Int, z: Int) {
                    val cell = Vector3d(x, y, z)

                    for(baseVector in baseVectors) {
                        val distance = cell distanceTo baseVector

                        if(!distance.approxEq(0.0)) {
                            if(((cell - baseVector).normalized() cosAngle normal) < cosDeviationMax) {
                                continue
                            }
                        }

                        if(distance <= radiusUpper) {
                            val fz = when(face.axis) {
                                Direction.Axis.X -> cell.x - baseVector.x
                                Direction.Axis.Y -> cell.y - baseVector.y
                                Direction.Axis.Z -> cell.z - baseVector.z
                                else -> error("Invalid axis ${face.axis}")
                            }.absoluteValue

                            val brightness = 15.0 * (1.0 - (fz / radius))

                            if(brightness >= 1.0) {
                                val k = BlockPosInt.pack(x, y, z)
                                fieldBase.put(k, max(fieldBase.get(k), brightness))
                            }
                        }
                    }
                }

                when(face) {
                    Direction.DOWN -> {
                        for (x in -radiusUpper..radiusUpper) {
                            for (y in -radiusUpper..0) {
                                for (z in -radiusUpper..radiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.UP -> {
                        for (x in -radiusUpper..radiusUpper) {
                            for (y in 0..radiusUpper) {
                                for (z in -radiusUpper..radiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.NORTH -> {
                        for (x in -radiusUpper..radiusUpper) {
                            for (y in -radiusUpper..radiusUpper) {
                                for (z in -radiusUpper..0) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.SOUTH -> {
                        for (x in -radiusUpper..radiusUpper) {
                            for (y in -radiusUpper..radiusUpper) {
                                for (z in 0..radiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.WEST -> {
                        for (x in -radiusUpper..0) {
                            for (y in -radiusUpper..radiusUpper) {
                                for (z in -radiusUpper..radiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                    Direction.EAST -> {
                        for (x in 0..radiusUpper) {
                            for (y in -radiusUpper..radiusUpper) {
                                for (z in -radiusUpper..radiusUpper) {
                                    set(x, y, z)
                                }
                            }
                        }
                    }
                }

                lateralPass(face, fieldBase)

                if(fieldBase.size > 0) {
                    for ((k, v) in fieldBase) {
                        results.put(k, round(v).toInt().coerceIn(0, 15).toByte())
                    }
                }
            }
        }

        return FaceOrientedLightVolumeProvider(variantsByFace)
    }

    private fun lateralPass(face: Direction, grid: Int2DoubleOpenHashMap) {
        val axis = face.axis

        val increment = Vector3di(face.stepX, face.stepY, face.stepZ)
        var columnPosition = Vector3di.zero

        val queue = IntArrayFIFOQueue()
        val plane = IntOpenHashSet()

        while (true) {
            val kColumn = BlockPosInt.pack(columnPosition)

            if(grid.get(kColumn) < 1) {
                break
            }

            var radiusSqr = 0
            var minBrightness = Double.MAX_VALUE

            queue.enqueue(kColumn)

            while (queue.size() > 0) {
                val front = queue.dequeueInt()
                val brightness = grid.get(front)

                if(brightness < 1) {
                    continue
                }

                if(!plane.add(front)) {
                    continue
                }

                if(brightness < minBrightness) {
                    minBrightness = brightness
                }

                val x = BlockPosInt.unpackX(front)
                val y = BlockPosInt.unpackY(front)
                val z = BlockPosInt.unpackZ(front)

                val dx = x - columnPosition.x
                val dy = y - columnPosition.y
                val dz = z - columnPosition.z

                radiusSqr = max(radiusSqr, dx * dx + dy * dy + dz * dz)

                when(axis) {
                    Direction.Axis.X -> {
                        queue.enqueue(BlockPosInt.pack(x, y + 1, z))
                        queue.enqueue(BlockPosInt.pack(x, y - 1, z))
                        queue.enqueue(BlockPosInt.pack(x, y, z + 1))
                        queue.enqueue(BlockPosInt.pack(x, y, z - 1))
                    }
                    Direction.Axis.Y -> {
                        queue.enqueue(BlockPosInt.pack(x + 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x - 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x, y, z + 1))
                        queue.enqueue(BlockPosInt.pack(x, y, z - 1))
                    }
                    Direction.Axis.Z -> {
                        queue.enqueue(BlockPosInt.pack(x + 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x - 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x, y + 1, z))
                        queue.enqueue(BlockPosInt.pack(x, y - 1, z))
                    }
                    else -> error("Invalid axis $axis")
                }
            }

            plane.remove(kColumn)

            val radius = sqrt(radiusSqr.toDouble())

            val iterator = plane.iterator()
            while (iterator.hasNext()) {
                val k = iterator.nextInt()

                val dx = columnPosition.x - BlockPosInt.unpackX(k)
                val dy = columnPosition.y - BlockPosInt.unpackY(k)
                val dz = columnPosition.z - BlockPosInt.unpackZ(k)

                val distance = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                grid.put(k, grid.get(k) * (1.0 - (distance / radius).coerceIn(0.0, 1.0)))
            }

            plane.clear()

            columnPosition += increment
        }
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
 * Provider for a [LightVolume], parameterized on the spatial configuration of the light emitter.
 * */
fun interface LightVolumeProvider {
    fun getVolume(locatorSet: Locator) : LightVolume
}

/**
 * *Light Volume* (light voxel data) function. This function maps a state increment (light "brightness") to the
 * desired light voxels.
 * */
class LightVolume private constructor(private val variants: Map<Int, Int2ByteMap>, private val mask: IntSet) {
   companion object {
       fun createImmutableStorage(source: Map<Int, Map<Int, Byte>>) : Int2ObjectOpenHashMap<Int2ByteMap> {
           val result = Int2ObjectOpenHashMap<Int2ByteMap>()

           source.forEach { (k, v) ->
               result.put(k, Int2ByteMaps.unmodifiable(Int2ByteOpenHashMap(v)))
           }

           return result
       }

       fun createMask(source: Map<Int, Map<Int, Byte>>) : IntSet {
           val mask = IntOpenHashSet()

           source.values.forEach {
               it.forEach { (k, v) ->
                   mask.add(k)
               }
           }

           return IntSets.unmodifiable(mask)
       }
   }

    /**
     * Gets the number of "state increments" (number of variants of the light field when the emitter is **powered**)
     * The *state* is given by the [LightTemperatureFunction] (whose co-domain is 0-1).
     * The temperature gets mapped to a discrete state (represented as an integer), and will be in the range [0, [stateIncrements]].
     * This means *[stateIncrements] + 1* states should be handled, the state "0" corresponding to the light when temperature is 0.
     * */
    val stateIncrements: Int

    init {
        require(variants.isNotEmpty()) {
            "Variant map was empty"
        }

        for (i in 0 until variants.size) {
            require(variants.containsKey(i)) {
                "Variant map did not have state increment $i"
            }
        }

        stateIncrements = variants.size - 1
    }

    constructor(variantsByState: Map<Int, Map<Int, Byte>>) : this(
        createImmutableStorage(variantsByState),
        createMask(variantsByState)
    )

    /**
     * Gets the desired light field, based on the current state of the light source, as per [view].
     * */
    fun getLightField(state: Int) : Int2ByteMap = variants[state] ?: error("Did not have variant $state")

    // In the future, maybe implement an algorithm to generate the mask, that fills in gaps (this would allow light fields with holes)

    /**
     * Gets a set of positions, that includes the positions of all cells (in all states).
     * */
    fun getMask() : IntSet = mask
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

class LightCell(ci: CellCreateInfo, poleMap: PoleMap) : Cell(ci), DataContainer, WailaEntity, LightView {
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
        subs.addPre(this::simulationTick)
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
            // Using this new "place" API, the game object will receive one event (with the latest values),
            // even if we do multiple updates in our simulation thread:
            gameEventReceiver.place(VolumetricLightChangeEvent(volume, targetState))
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

class LightPart(
    id: ResourceLocation,
    placementContext: PartPlacementInfo,
    cellProvider: CellProvider<LightCell>
) : CellPart<LightCell, LightFixtureRenderer>(id, placementContext, cellProvider), EventListener, RotatablePart {
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

    private val lightCells = Int2ObjectOpenHashMap<Light>()
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

    override fun createRenderer() = LightFixtureRenderer(
        this,
        PartialModels.SMALL_WALL_LAMP_CAGE,
        PartialModels.SMALL_WALL_LAMP_EMITTER
    )

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

        // Item is only mutated on onUsedBy (server thread), when the bulb is added/removed, so it is safe to access here
        // if it is null, it means we got this update possibly after the bulb was removed by a player, so we will ignore it
        if(!hasCell || cell.item == null) {
            return
        }

        if(this.volume == null) {
            this.volume = event.volume
        }

        require(this.volume == event.volume)

        if(event.targetState == state) {
            return
        }

        val volume = this.volume!!

        // We first do a pass on the keys in the actual field.
        // This way, we can detect cells that are no longer present in the target field, or got updated.
        // While we do that, we remove cells that we processed from a set that has the cells in the target field.
        // After we finished processing the positions in the actual field, this set will have all cells that were not present in the
        // actual field, so they are new

        val actualField = volume.getLightField(this.state)
        val targetField = volume.getLightField(event.targetState)
        val newCells = IntOpenHashSet(targetField.keys)

        val actualFieldIterator = actualField.keys.intIterator()
        while (actualFieldIterator.hasNext()) {
            val k = actualFieldIterator.nextInt()

            newCells.remove(k)

            val newBrightness = targetField.getOrDefault(k, 0)
            val cell = lightCells[k] ?: continue // Out of bounds?

            if(newBrightness < 1) {
                // No longer present in target brightness field:
                cell.updateBrightness(0)
            } else {
                // Present in both, updated:
                cell.updateBrightness(newBrightness.toInt())
            }
        }

        val newCellsIterator = newCells.intIterator()
        while (newCellsIterator.hasNext()) {
            val k = newCellsIterator.nextInt()
            val positionWorld = placement.position + BlockPosInt.unpackBlockPos(k)

            if(!GhostLightServer.canCreateHandle(placement.level, positionWorld)) {
                continue
            }

            var cell = lightCells.get(k)

            if(cell == null) {
                cell = Light(k, positionWorld)
                require(lightCells.put(k, cell) == null)
            }

            cell.updateBrightness(targetField.get(k).toInt())
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

        if(!placement.level.isClientSide) {
            destroyCells()
        }
    }

    private inner class Light(val voxelPositionVolume: Int, voxelPositionWorld: BlockPos) {
        private val handle = GhostLightServer.createHandle(placement.level, voxelPositionWorld) { _, type ->
            onUpdate(type)
        }!!

        /**
         * Set of all voxel cells that are obstructing the ray.
         * These positions are in the volume frame.
         * */
        private val obstructingBlocks = IntOpenHashSet()
        private val isObstructed get() = obstructingBlocks.size > 0
        private val isOrigin get() = BlockPosInt(voxelPositionVolume).isZero

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

            val mask = volume!!.getMask()

            val intersectionsVolume = rayIntersectionAnalyzer(voxelPositionVolume) { k ->
                mask.contains(k)
            }

            LOG.info("Intersected ${intersectionsVolume.size}")

            val iterator = intersectionsVolume.intIterator()

            when(type) {
                GhostLightUpdateType.Closed -> {
                    // Add to obstructions list and update:
                    while (iterator.hasNext()) {
                        val cell = lightCells[iterator.nextInt()] ?: continue
                        cell.obstructingBlocks.add(voxelPositionVolume)
                        // If we added one, it automatically means the cell is blocked:
                        cell.handle.setBrightness(0)
                    }
                }
                GhostLightUpdateType.Opened -> {
                    // Remove from list of obstructions and, if empty, set light:
                    while (iterator.hasNext()) {
                        val cell = lightCells[iterator.nextInt()] ?: continue

                        if(cell.obstructingBlocks.remove(voxelPositionVolume)) {
                            if(!cell.isObstructed) {
                                cell.handle.setBrightness(cell.desiredBrightness)
                            }
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

class LightFixtureRenderer(
    val part: LightPart,
    val cageModel: PartialModel,
    val emitterModel: PartialModel,
    val coldTint: Color = Color(255, 255, 255, 255),
    val warmTint: Color = Color(254, 196, 127, 255)
) : PartRenderer() {
    private val brightnessUpdate = AtomicUpdate<Double>()
    private var brightness = 0.0

    fun updateBrightness(newValue: Double) = brightnessUpdate.setLatest(newValue)

    var yRotation = 0.0

    private var cageInstance: ModelData? = null
    private var emitterInstance: ModelData? = null

    override fun setupRendering() {
        cageInstance?.delete()
        emitterInstance?.delete()
        cageInstance = create(cageModel)
        emitterInstance = create(emitterModel)
        applyLightTint()
    }

    private fun create(model: PartialModel): ModelData {
        return multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .transformPart(part, yRotation)
    }

    private fun applyLightTint() {
        emitterInstance?.setColor(colorLerp(coldTint, warmTint, brightness.toFloat()))
    }

    override fun relight(source: RelightSource) {
        multipart.relightModels(emitterInstance, cageInstance)
    }

    override fun beginFrame() {
        brightnessUpdate.consume {
            brightness = it.coerceIn(0.0, 1.0)
            applyLightTint()
        }
    }

    override fun remove() {
        cageInstance?.delete()
        emitterInstance?.delete()
    }
}
