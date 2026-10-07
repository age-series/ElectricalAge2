package org.eln2.mc

import com.google.gson.JsonObject
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.classify
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.map
import org.eln2.mc.common.ForgeEvents
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.specs.foundation.SpecContainerPart
import org.eln2.mc.extensions.minus
import org.eln2.mc.extensions.toVector3d
import org.eln2.mc.extensions.viewClip
import org.eln2.mc.mathematics.FacingDirection
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*
import kotlin.random.Random

fun randomFloat(min: Float, max: Float) = map(Random.nextFloat(), 0f, 1f, min, max)

fun <T> all(vararg items: T, condition: (T) -> Boolean) = items.asList().all(condition)

val digitRange = '0'..'9'
val subscriptDigitRange = '₀'..'₉'

operator fun CharRange.get(x: Int) = this.elementAt(x)

val Char.isLetter get() = this in 'a'..'z' || this in 'A'..'Z'
val Char.isDigit get() = this in digitRange
val Char.isSubscriptDigit get() = this in subscriptDigitRange
val Char.isDigitOrSubscriptDigit get() = this.isDigit || this.isSubscriptDigit

fun subscriptToDigit(c: Char): Char {
    require(c.isSubscriptDigit) { "$c is not a subscript digit" }
    return digitRange[subscriptDigitRange.indexOf(c)]
}

fun digitToSubscript(c: Char): Char {
    require(c.isDigit) { "$c is not a digit" }
    return subscriptDigitRange[digitRange.indexOf(c)]
}

fun Int.toStringSubscript() = String(this.toString().map { digitToSubscript(it) }.toCharArray())

fun charDigitValue(c: Char): Char {
    if (c.isDigit) return c
    if (c.isSubscriptDigit) return subscriptToDigit(c)
    error("$c is not a digit or subscript digit")
}

@Suppress("UNCHECKED_CAST")
fun <T : BlockEntity?, U> ticker(f: (pLevel: Level, pPos: BlockPos, pState: BlockState, pBlockEntity: U) -> Unit) =
    BlockEntityTicker<T> { level, pos, state, e ->
        f(level, pos, state, e as U)
    }

fun <T> clipScene(entity: LivingEntity, access: ((T) -> AABB), objects: Collection<T>): T? {
    val intersections = LinkedHashMap<Vec3, T>()

    val eyePos = Vec3(entity.x, entity.eyeY, entity.z)

    objects.forEach { obj ->
        val box = access(obj)

        val intersection = box.viewClip(entity)

        if (!intersection.isEmpty) {
            intersections[intersection.get()] = obj
        }
    }

    val entry = intersections.minByOrNull { entry ->
        (eyePos - entry.key).length()
    }

    return entry?.value
}

fun componentMin(a: Vector3f, b: Vector3f): Vector3f {
    return Vector3f(
        min(a.x(), b.x()),
        min(a.y(), b.y()),
        min(a.z(), b.z())
    )
}

fun componentMax(a: Vector3f, b: Vector3f): Vector3f {
    return Vector3f(
        max(a.x(), b.x()),
        max(a.y(), b.y()),
        max(a.z(), b.z())
    )
}

fun isServerThread() = ServerLifecycleHooks.getCurrentServer().isSameThread
fun isRenderThread() = RenderSystem.isOnRenderThread()

inline fun requireIsOnServerThread(message: () -> String) = require(isServerThread()) {
    message()
}

fun requireIsOnServerThread() = requireIsOnServerThread { "Requirement failed: not on server thread (${Thread.currentThread()})" }

inline fun requireIsOnRenderThread(message: () -> String) = require(isRenderThread()) {
    message()
}

fun requireIsOnRenderThread() = requireIsOnRenderThread {
    "Requirement failed: not on render thread (${Thread.currentThread()})"
}

fun<K, V> ConcurrentHashMap<K, V>.atomicRemoveIf(consumer: (Map.Entry<K, V>) -> Boolean) {
    this.entries.forEach { entry ->
        if(consumer(entry)) {
            this.remove(entry.key, entry.value)
        }
    }
}

private fun validateSide(side: Dist) = when(side) {
    Dist.CLIENT -> requireIsOnRenderThread {
        "Accessed client only"
    }
    Dist.DEDICATED_SERVER -> requireIsOnServerThread {
        "Accessed server only"
    }
}
class SidedLazy<T>(factory: () -> T, val side: Dist) {
    private val lazy = lazy(factory)

    fun get() : T {
        validateSide(side)
        return lazy.value
    }

    operator fun invoke() = get()
}

fun<T> clientOnlyHolder(factory: () -> T) = SidedLazy(factory, Dist.CLIENT)
fun<T> serverOnlyHolder(factory: () -> T) = SidedLazy(factory, Dist.DEDICATED_SERVER)

private val UNIQUE_ID_ATOMIC = AtomicInteger()

fun getUniqueId() = UNIQUE_ID_ATOMIC.getAndIncrement()

fun directionByNormal(normal: Vec3i) = Direction.entries.firstOrNull { it.normal == normal }

fun isServerPaused() : Boolean {
    val server = ServerLifecycleHooks.getCurrentServer()

    if(server == null) {
        LOG.fatal(DEBUGGER_BREAK("ELN2: SERVER NULL"))
        return true
    }

    if(server is IntegratedServer) {
        return server.paused
    }

    val time = ForgeEvents.timeSinceLastTick

    if(time > 1.0) {
        LOG.fatal("ELN2: FOUND ${time.classify()} SINCE LAST SERVER TICK")
        return true
    }

    return false
}

inline fun<reified T> buildDirectionTable(transform: (Direction) -> T) = Direction.entries
    .map { it to transform(it) }
    .sortedBy { it.first.get3DDataValue() }
    .map { it.second }

inline fun<reified T> buildHorizontalFacingTable(transform: (FacingDirection) -> T) = FacingDirection.entries
    .map { it to transform(it) }
    .sortedBy { it.first.index }
    .map { it.second }

enum class ItemPersistentLoadOrder {
    /**
     * The data is loaded before the simulation is built.
     * */
    BeforeSim,
    /**
     * The data is loaded after the simulation is built.
     * */
    AfterSim
}

interface ItemPersistent {
    val order: ItemPersistentLoadOrder

    /**
     * Saves the part to an item tag.
     * */
    fun saveToItemNbt(tag: CompoundTag)

    /**
     * Loads the part from the item tag.
     * @param tag The saved tag. Null if no data was present in the item (possibly because the item was newly created)
     * */
    fun loadFromItemNbt(tag: CompoundTag?)
}

fun getPlayerPOVHitResult(pLevel: Level, pPlayer: Player): BlockHitResult {
    val f = pPlayer.xRot
    val f1 = pPlayer.yRot
    val vec3 = pPlayer.eyePosition
    val f2 = cos(-f1 * (PI.toFloat() / 180f) - PI.toFloat())
    val f3 = sin(-f1 * (PI.toFloat() / 180f) - PI.toFloat())
    val f4 = -cos(-f * (PI.toFloat() / 180f))
    val f5 = sin(-f * (PI.toFloat() / 180f))
    val f6 = f3 * f4
    val f7 = f2 * f4
    val d0 = pPlayer.getBlockReach()
    val vec31 = vec3.add(f6.toDouble() * d0, f5.toDouble() * d0, f7.toDouble() * d0)
    return pLevel.clip(
        ClipContext(
            vec3,
            vec31,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.SOURCE_ONLY,
            pPlayer
        )
    )
}

/**
 * Result of picking a game object under the player's crosshair.
 * @param target The picked object (block entity, part, or spec).
 * @param position A representative world position for the object (used for sound playback, etc).
 * @param side The world-space face the crosshair hit, or null if the pick didn't resolve to a face (e.g. a spec picked by its oriented bounding box rather than a block face).
 * */
data class GameObjectPick<T>(val target: T, val position: Vector3d, val side: Direction?)

/**
 * Picks the game object under the player's crosshair, resolving through block entities, multipart parts, and specs.
 * Generic so callers can request a specific type (e.g. [org.eln2.mc.client.overlays.HoverDetailSupplier]).
 * Returns null if nothing under the crosshair matches [T].
 * */
inline fun <reified T> pickGameObject(player: Player?): GameObjectPick<T>? {
    if(player == null) {
        return null
    }

    val level = player.level() ?: return null

    val hit = getPlayerPOVHitResult(level, player)

    if(hit.type != HitResult.Type.BLOCK) {
        return null
    }

    val side = hit.direction
    val targetBlockEntity = level.getBlockEntity(hit.blockPos) ?: return null

    if(targetBlockEntity is T) {
        return GameObjectPick(targetBlockEntity, targetBlockEntity.blockPos.toVector3d() + Vector3d.one * 0.5, side)
    }

    val multipart = targetBlockEntity as? MultipartBlockEntity ?: return null

    val part = multipart.pickPart(player) ?: return null

    if(part is T) {
        val center = part.worldBoundingBox.center
        return GameObjectPick(part, Vector3d(center.x, center.y, center.z), side)
    }

    val specContainer = part as? SpecContainerPart ?: return null

    val spec = specContainer.pickSpec(player)?.second ?: return null

    if(spec is T) {
        return GameObjectPick(spec, spec.placement.orientedBoundingBoxWorld.center, side)
    }

    return null
}

class PIDController(var kP: Double, var kI: Double, var kD: Double) {
    constructor() : this(1.0, 0.0, 0.0)

    var errorSum = 0.0
    var lastError = 0.0

    /**
     * Gets or sets the setpoint (desired value).
     * */
    var setPoint = 0.0

    /**
     * Gets or sets the minimum control signal returned by [update]
     * */
    var minControl = -Double.MAX_VALUE

    /**
     * Gets or sets the maximum control signal returned by [update]
     * */
    var maxControl = Double.MAX_VALUE

    fun update(value: Double, dt: Double): Double {
        val error = setPoint - value
        val derivative = (error - lastError) / dt

        val prospectiveErrorSum = errorSum + (error + lastError) * 0.5 * dt
        val output = kP * error + kI * prospectiveErrorSum + kD * derivative

        val windup = (output > maxControl && error > 0.0) ||
            (output < minControl && error < 0.0)

        if (!windup) {
            errorSum = prospectiveErrorSum
        }

        lastError = error

        return output.coerceIn(minControl, maxControl)
    }

    fun reset() {
        errorSum = 0.0
        lastError = 0.0
    }
}

data class PIDGains(val kP: Double, val kI: Double, val kD: Double)

fun fluidStackToJson(fluid: FluidStack): JsonObject {
    return JsonObject().also { obj ->
        obj.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(fluid.fluid)!!.toString())
        obj.addProperty("amount", fluid.amount)
    }
}

fun itemStackToJson(stack: ItemStack): JsonObject {
    return JsonObject().also { obj ->
        obj.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.item)!!.toString())
        obj.addProperty("count", stack.count)
    }
}
