@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.eln2.mc.common.content

import net.minecraft.client.Minecraft
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item
import org.ageseries.libage.mathematics.smoothstep
import org.eln2.mc.Eln2Config
import org.eln2.mc.requireIsOnRenderThread
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.approxEq
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.client.dynamicLight.DynamicLightManager
import org.eln2.mc.common.network.Networking
import org.eln2.mc.client.dynamicLight.DynamicLightSource
import org.joml.Vector3f
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.Util
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraftforge.client.ForgeHooksClient
import java.util.*
import java.util.function.Supplier
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Specification for the [FlashlightItem].
 *
 * @param powerDemand The power drawn continuously while held and active.
 * @param nominalIntensity The light intensity at full power.
 * @param nominalRange The light range at full power, in meters.
 * @param halfAngleDeg The cone half-angle in degrees.
 * @param color The light color as RGB.
 * */
data class FlashlightModel(
    val powerDemand: Quantity<Power>,
    val nominalIntensity: Float,
    val nominalRange: Float,
    val halfAngleDeg: Float,
    val color: Vector3f,
)

/**
 * Per-player power consumer for the [FlashlightItem]. Created when a flashlight is held, destroyed when it leaves the player's hands.
 * Reports a constant demand to [PlayerPowerManager] while active. The granted/demand fraction modulates the flashlight's intensity and range.
 * */
private class FlashlightPowerConsumer(
    private val player: ServerPlayer,
    private val flashlight: FlashlightItem,
) : InventoryPowerConsumer {
    override val priority = InventoryPowerPriority.Low

    var grantedThisTick = 0.0
        private set

    var demandThisTick = 0.0
        private set

    private var ticked = false

    var fraction: Float = 0.0f
        private set

    override fun powerDemand(): Quantity<Power> {
        if (!isHoldingFlashlight(player, flashlight)) {
            return Quantity(0.0, WATT)
        }
        return flashlight.flashlightModel.powerDemand
    }

    override fun receivePower(granted: Quantity<Energy>): Quantity<Energy> {
        grantedThisTick += !granted
        return granted
    }

    override fun preTick() {
        grantedThisTick = 0.0
        demandThisTick = !powerDemand()
        ticked = true
    }

    override fun postTick(dt: Double) {
        fraction = if (!ticked || demandThisTick.approxEq(0.0)) {
            0.0f
        } else {
            (grantedThisTick / (demandThisTick * dt)).toFloat().coerceIn(0.0f, 1.0f)
        }
    }
}

private fun isHoldingFlashlight(player: Player, flashlight: FlashlightItem): Boolean {
    return player.mainHandItem.item === flashlight || player.offhandItem.item === flashlight
}

/**
 * An electrically-powered flashlight. Draws energy from [PowerCellItem]s in the player's inventory via [PlayerPowerManager].
 * While held, it registers a [DynamicLightSource] with [DynamicLightManager] on the client, producing a shadow-cast cone light.
 * The light's intensity and range are modulated by the power fraction (granted energy / demand).
 * Works in multiplayer: each player holding a flashlight produces a light source on all nearby clients.
 * The power fraction is computed server-side and synced to all tracking players via [FlashlightPowerMessage].
 * */
class FlashlightItem(val flashlightModel: FlashlightModel) : Item(Properties().stacksTo(1)) {
    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        val yellow = ChatFormatting.YELLOW
        val gray = ChatFormatting.GRAY

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.flashlight.power")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(flashlightModel.powerDemand)).withStyle(gray))
                .withStyle(yellow)
        )
    }
    companion object {
        private const val DT = 1.0 / 20.0
        private const val SYNC_INTERVAL_TICKS = 5

        private val consumers = HashMap<UUID, FlashlightPowerConsumer>()
        private val syncTickCounters = HashMap<UUID, Int>()

        fun clearAll() {
            consumers.clear()
            syncTickCounters.clear()
        }

        fun clearPlayer(uuid: UUID) {
            consumers.remove(uuid)
            syncTickCounters.remove(uuid)
        }

        /**
         * Called from [org.eln2.mc.common.ForgeEvents] on PlayerTickEvent END, after [PlayerPowerManager.tick].
         * Manages the flashlight power consumer lifecycle and syncs the fraction to tracking players.
         * */
        fun onPlayerTickEnd(player: ServerPlayer) {
            val holdingFlashlight = findHeldFlashlight(player)

            if (holdingFlashlight == null) {
                consumers.remove(player.uuid)?.let {
                    PlayerPowerManager.unregisterConsumer(player, it)
                }
                syncTickCounters.remove(player.uuid)
                return
            }

            val consumer = consumers[player.uuid]
            if (consumer == null) {
                val newConsumer = FlashlightPowerConsumer(player, holdingFlashlight)
                consumers[player.uuid] = newConsumer
                PlayerPowerManager.registerConsumer(player, newConsumer)
                return
            }

            val counter = syncTickCounters.getOrDefault(player.uuid, 0) + 1
            syncTickCounters[player.uuid] = counter

            if (counter >= SYNC_INTERVAL_TICKS) {
                syncTickCounters[player.uuid] = 0
                Networking.sendToTrackingAndSelf(
                    FlashlightPowerMessage(player.uuid, consumer.fraction),
                    player
                )
            }
        }

        private fun findHeldFlashlight(player: ServerPlayer): FlashlightItem? {
            val mainHand = player.mainHandItem.item as? FlashlightItem
            if (mainHand != null) {
                return mainHand
            }
            return player.offhandItem.item as? FlashlightItem
        }

        private val clientLightSources = HashMap<UUID, DynamicLightSource>()
        private var updateCallbackRegistered = false

        /**
         * Called from client setup to register the per-frame light source update callback with [DynamicLightManager].
         * */
        fun registerClient() {
            if (updateCallbackRegistered) {
                return
            }
            DynamicLightManager.addUpdateCallback(::updateClientLightSources)
            updateCallbackRegistered = true
        }

        /**
         * Clears all client-side light sources. Called on level unload / disconnect.
         * */
        fun clearClient() {
            requireIsOnRenderThread()
            for (source in clientLightSources.values) {
                DynamicLightManager.removeLightSource(source)
            }
            clientLightSources.clear()
            FlashlightPowerMessage.clearClient()
            FlashlightHandAnchors.clear()
        }

        /**
         * Per-frame callback that scans all players in the client level, registering/unregistering [DynamicLightSource]s
         * for players holding a [FlashlightItem], and updating each source's intensity from the synced power fraction.
         * */
        private fun updateClientLightSources(level: ClientLevel, partialTick: Float) {
            requireIsOnRenderThread()
            val seenPlayers = HashSet<UUID>()

            for (player in level.players()) {
                val flashlight = findHeldFlashlightClient(player) ?: continue
                val uuid = player.uuid
                seenPlayers.add(uuid)

                if (!clientLightSources.containsKey(uuid)) {
                    val source = createClientLightSource(player, flashlight)
                    clientLightSources[uuid] = source
                }

                val source = clientLightSources[uuid]!!
                val fraction = FlashlightPowerMessage.getClientFraction(uuid)
                applyFraction(source, flashlight, fraction)
            }

            val removed = ArrayList<UUID>()
            for ((uuid, source) in clientLightSources) {
                if (uuid !in seenPlayers) {
                    DynamicLightManager.removeLightSource(source)
                    removed.add(uuid)
                }
            }
            for (uuid in removed) {
                clientLightSources.remove(uuid)
            }
        }

        private fun findHeldFlashlightClient(player: AbstractClientPlayer): FlashlightItem? {
            val mainHand = player.mainHandItem.item as? FlashlightItem
            if (mainHand != null) {
                return mainHand
            }
            return player.offhandItem.item as? FlashlightItem
        }

        private fun createClientLightSource(
            player: AbstractClientPlayer,
            flashlight: FlashlightItem,
        ): DynamicLightSource {
            val model = flashlight.flashlightModel
            val pose = FlashlightClientPose(player, model.nominalRange.toDouble())
            return DynamicLightManager.createLightSource(
                poseUpdater = pose::compute,
                color = model.color,
                intensity = 0.0f,
                range = model.nominalRange,
                halfAngleDeg = model.halfAngleDeg,
                ownerEntity = player,
            )
        }

        private fun applyFraction(source: DynamicLightSource, flashlight: FlashlightItem, fraction: Float) {
            val model = flashlight.flashlightModel
            val smoothFraction = smoothstep(fraction.toDouble()).toFloat()
            source.intensity = model.nominalIntensity * smoothFraction
            source.range = model.nominalRange * (0.3f + 0.7f * smoothFraction)
        }
    }
}

object FlashlightHandAnchors {
    private const val MAXIMUM_ANCHOR_AGE_FRAMES = 2L
    private const val PRUNE_THRESHOLD = 64

    private val lensModelPosition = Vector3f(0.85f, 0.85f, 0.5f)
    private val anchors = HashMap<Long, HandAnchor>()

    private class HandAnchor(val viewPosition: Vector3f, val frameIndex: Long)

    fun capture(
        entity: LivingEntity,
        stack: ItemStack,
        context: ItemDisplayContext,
        leftHand: Boolean,
        poseStack: PoseStack,
    ) {
        if (stack.item !is FlashlightItem) {
            return
        }

        val isThirdPersonHand = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ||
            context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND

        if (!context.firstPerson() && !(isThirdPersonHand && DynamicLightManager.isRenderingLevelEntities)) {
            return
        }

        val modelSeed = entity.id + context.ordinal
        val model = Minecraft.getInstance().itemRenderer.getModel(stack, entity.level(), entity, modelSeed)
        val lensTransform = PoseStack()
        lensTransform.mulPoseMatrix(poseStack.last().pose())
        ForgeHooksClient.handleCameraTransforms(lensTransform, model, context, leftHand)
        lensTransform.translate(-0.5f, -0.5f, -0.5f)

        val arm = if (leftHand) HumanoidArm.LEFT else HumanoidArm.RIGHT
        val hand = if (arm == entity.mainArm) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        val viewPosition = lensTransform.last().pose().transformPosition(Vector3f(lensModelPosition))

        if (anchors.size > PRUNE_THRESHOLD) {
            anchors.values.removeIf { isStale(it) }
        }

        anchors[anchorKey(entity, hand)] = HandAnchor(viewPosition, DynamicLightManager.frameIndex)
    }

    fun cameraRelativePosition(entity: Entity, hand: InteractionHand): Vector3f? {
        val anchor = anchors[anchorKey(entity, hand)] ?: return null

        if (isStale(anchor)) {
            return null
        }

        return DynamicLightManager.cameraRelativeFromView(anchor.viewPosition)
    }

    fun clear() {
        anchors.clear()
    }

    private fun isStale(anchor: HandAnchor): Boolean {
        return DynamicLightManager.frameIndex - anchor.frameIndex > MAXIMUM_ANCHOR_AGE_FRAMES
    }

    private fun anchorKey(entity: Entity, hand: InteractionHand): Long {
        val handIndex = if (hand == InteractionHand.MAIN_HAND) 0L else 1L
        return (entity.id.toLong() shl 1) or handIndex
    }
}

private class FlashlightClientPose(private val player: AbstractClientPlayer, private val maximumDistance: Double) {
    private var convergenceDistance = Double.NaN
    private var lastUpdateNanos = 0L

    fun compute(partialTick: Float): Pair<Vec3, Vec3> {
        val eyePosition = player.getEyePosition(partialTick)
        val lookDirection = player.getViewVector(partialTick)
        val isInMainHand = player.mainHandItem.item is FlashlightItem
        val hand = if (isInMainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        val handPosition = keepOutOfBlocks(eyePosition, findHandPosition(hand, eyePosition, lookDirection, partialTick))
        val target = eyePosition.add(lookDirection.scale(updateConvergenceDistance(eyePosition, partialTick)))
        val direction = target.subtract(handPosition)

        if (direction.lengthSqr() < 1.0e-6) {
            return Pair(handPosition, lookDirection)
        }

        return Pair(handPosition, direction.normalize())
    }

    private fun findHandPosition(
        hand: InteractionHand,
        eyePosition: Vec3,
        lookDirection: Vec3,
        partialTick: Float,
    ): Vec3 {
        val anchor = FlashlightHandAnchors.cameraRelativePosition(player, hand)

        if (anchor != null) {
            val cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera.position
            return cameraPosition.add(anchor.x.toDouble(), anchor.y.toDouble(), anchor.z.toDouble())
        }

        val arm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
        val side = if (arm == HumanoidArm.RIGHT) 1.0 else -1.0
        val minecraft = Minecraft.getInstance()

        if (player === minecraft.cameraEntity && minecraft.options.cameraType.isFirstPerson) {
            val yaw = Math.toRadians(player.getViewYRot(partialTick).toDouble())
            val right = Vec3(-cos(yaw), 0.0, -sin(yaw))
            val up = right.cross(lookDirection)

            return eyePosition
                .add(lookDirection.scale(FIRST_PERSON_FORWARD_OFFSET))
                .add(right.scale(FIRST_PERSON_SIDE_OFFSET * side))
                .add(up.scale(-FIRST_PERSON_DOWN_OFFSET))
        }

        val bodyYaw = Math.toRadians(Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot).toDouble())
        val bodyRight = Vec3(-cos(bodyYaw), 0.0, -sin(bodyYaw))
        val bodyForward = Vec3(-sin(bodyYaw), 0.0, cos(bodyYaw))
        val handHeight = if (player.isCrouching) CROUCHING_HAND_HEIGHT else STANDING_HAND_HEIGHT

        return player.getPosition(partialTick)
            .add(0.0, handHeight, 0.0)
            .add(bodyRight.scale(THIRD_PERSON_SIDE_OFFSET * side))
            .add(bodyForward.scale(THIRD_PERSON_FORWARD_OFFSET))
    }

    private fun keepOutOfBlocks(eyePosition: Vec3, handPosition: Vec3): Vec3 {
        val context = ClipContext(eyePosition, handPosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        val hit = player.level().clip(context)

        if (hit.type == HitResult.Type.MISS) {
            return handPosition
        }

        val offset = handPosition.subtract(eyePosition)
        val length = offset.length()

        if (length < WALL_CLEARANCE) {
            return eyePosition
        }

        return hit.location.subtract(offset.scale(WALL_CLEARANCE / length))
    }

    private fun updateConvergenceDistance(eyePosition: Vec3, partialTick: Float): Double {
        val hit = player.pick(maximumDistance, partialTick, false)
        val hitDistance = if (hit.type == HitResult.Type.MISS) maximumDistance else hit.location.distanceTo(eyePosition)
        val targetDistance = hitDistance.coerceIn(MINIMUM_CONVERGENCE_DISTANCE, maximumDistance)
        val now = Util.getNanos()

        convergenceDistance = if (convergenceDistance.isNaN()) {
            targetDistance
        } else {
            val elapsedSeconds = (now - lastUpdateNanos) * 1.0e-9
            val blend = 1.0 - exp(-elapsedSeconds * CONVERGENCE_RATE)
            convergenceDistance + (targetDistance - convergenceDistance) * blend
        }

        lastUpdateNanos = now
        return convergenceDistance
    }

    companion object {
        private const val FIRST_PERSON_FORWARD_OFFSET = 0.7
        private const val FIRST_PERSON_SIDE_OFFSET = 0.5
        private const val FIRST_PERSON_DOWN_OFFSET = 0.45
        private const val THIRD_PERSON_FORWARD_OFFSET = 0.2
        private const val THIRD_PERSON_SIDE_OFFSET = 0.37
        private const val STANDING_HAND_HEIGHT = 0.75
        private const val CROUCHING_HAND_HEIGHT = 0.55
        private const val WALL_CLEARANCE = 0.05
        private const val MINIMUM_CONVERGENCE_DISTANCE = 1.5
        private const val CONVERGENCE_RATE = 12.0
    }
}

/**
 * Server-to-client message syncing a player's flashlight power fraction to all tracking clients.
 * Sent via [PacketDistributor.TRACKING_ENTITY_AND_SELF] so every player who can see the flashlight holder
 * knows how bright the light should be.
 * */
class FlashlightPowerMessage(val playerId: UUID, val fraction: Float) {

    companion object {
        private val clientFractions = HashMap<UUID, Float>()

        fun getClientFraction(playerId: UUID): Float {
            return clientFractions.getOrDefault(playerId, 0.0f)
        }

        fun clearClient() {
            clientFractions.clear()
        }

        fun reset() {
            clientFractions.clear()
        }

        fun encode(message: FlashlightPowerMessage, buf: FriendlyByteBuf) {
            buf.writeUUID(message.playerId)
            buf.writeFloat(message.fraction)
        }

        fun decode(buf: FriendlyByteBuf): FlashlightPowerMessage {
            return FlashlightPowerMessage(buf.readUUID(), buf.readFloat())
        }

        fun handle(message: FlashlightPowerMessage, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                clientFractions[message.playerId] = message.fraction
            }
            ctx.get().packetHandled = true
        }
    }
}
