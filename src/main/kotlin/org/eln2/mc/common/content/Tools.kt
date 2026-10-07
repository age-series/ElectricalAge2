package org.eln2.mc.common.content

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.HitResult
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.network.NetworkEvent
import org.ageseries.libage.data.Energy
import org.ageseries.libage.data.OptionalDouble
import org.ageseries.libage.data.Power
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.WATT
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.approxEq
import org.eln2.mc.LOG
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.content.modules.Eln2Tools
import org.eln2.mc.client.screens.ScrewdriverConfigScreen
import org.eln2.mc.common.network.Networking
import org.eln2.mc.Eln2Config
import org.eln2.mc.extensions.plus
import java.util.*
import java.util.function.Supplier
import kotlin.random.Random

interface WrenchRotatable {
    @ServerOnly
    fun canRotateWithWrench(wrench: WrenchItem, context: UseOnContext): Boolean = true
}

interface WrenchInteractable {
    @ServerOnly
    fun applyWrench(wrench: WrenchItem, context: UseOnContext) : InteractionResult
}

class WrenchItem : Item(Properties().stacksTo(1)) {
    override fun useOn(pContext: UseOnContext): InteractionResult {
        if(pContext.level.isClientSide) {
            return InteractionResult.PASS
        }

        val player = pContext.player ?: return InteractionResult.FAIL
        val blockEntity = pContext.level.getBlockEntity(pContext.clickedPos)

        if(blockEntity is WrenchInteractable) {
            if(player.isShiftKeyDown) {
                return blockEntity.applyWrench(this, pContext)
            }

            return InteractionResult.FAIL
        }

        val multipart = blockEntity as? MultipartBlockEntity ?: return InteractionResult.FAIL

        val part = multipart.pickPart(player) ?: return InteractionResult.FAIL

        if(part is WrenchInteractable) {
            if(player.isShiftKeyDown) {
                return part.applyWrench(this, pContext)
            }
        }

        if(part !is WrenchRotatable || !part.canRotateWithWrench(this, pContext)) {
            return InteractionResult.FAIL
        }

        val tag = CompoundTag()

        multipart.breakPart(part, tag)

        val orientation = if(player.isShiftKeyDown) {
            part.placement.facing.clockWise
        }
        else {
            part.placement.facing.counterClockWise
        }

        val flag = multipart.place(
            player,
            part.placement.position,
            part.placement.face,
            part.placement.provider,
            saveTag = tag,
            orientation = orientation
        )

        if(!flag) {
            LOG.error("FAILED TO PLACE PART WITH WRENCH! $player ${part.placement.position} ${part.placement.face} ${part.placement.provider} $tag $orientation $part ${part.placement.provider}")
        }

        return InteractionResult.SUCCESS
    }

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        pTooltipComponents.add(Component.translatable("tooltip.eln2.wrench.use").withStyle(ChatFormatting.GRAY))
        pTooltipComponents.add(Component.translatable("tooltip.eln2.wrench.rotate").withStyle(ChatFormatting.DARK_GRAY))
    }
}

interface ScrewdriverInteractable {
    @ServerOnly
    fun applyScrewdriver(screwdriver: ScrewdriverItem, context: UseOnContext, configValue: OptionalDouble)
}

interface ScrewdriverScrollable {
    @ServerOnly
    fun scrollScrewdriver(player: ServerPlayer, delta: Double) : Boolean
}

class ScrewdriverItem : Item(Properties().stacksTo(1)) {
    override fun useOn(pContext: UseOnContext): InteractionResult {
        if(pContext.level.isClientSide) {
            return InteractionResult.PASS
        }

        val player = pContext.player ?: return InteractionResult.FAIL

        if(!player.isShiftKeyDown) {
            return InteractionResult.PASS
        }

        val interactable = pickGameObject<ScrewdriverInteractable>(player)?.first ?: return InteractionResult.FAIL
        val configValue = getConfigValue(pContext.itemInHand)
        interactable.applyScrewdriver(this, pContext, configValue)

        return InteractionResult.SUCCESS
    }

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = pPlayer.getItemInHand(pUsedHand)

        if(pPlayer.isShiftKeyDown) {
            if(!pLevel.isClientSide) {
                clearConfigValue(stack)
                pPlayer.displayClientMessage(CLEARED_MESSAGE, true)
            }

            return InteractionResultHolder.success(stack)
        }

        if(pLevel.isClientSide) {
            ScrewdriverConfigScreen.open()
        }

        return InteractionResultHolder.success(stack)
    }

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        pTooltipComponents.add(Component.translatable("tooltip.eln2.screwdriver.use").withStyle(ChatFormatting.GRAY))
        pTooltipComponents.add(Component.translatable("tooltip.eln2.screwdriver.scroll").withStyle(ChatFormatting.DARK_GRAY))
    }
    data class Scroll(val delta: Double) {
        companion object {
            // Cooldown for sounds. Noticed it is *really* important with my G502's infinite scroll.
            private const val COOLDOWN = 1
            private val cooldowns = WeakHashMap<Any, Int>()

            fun isDeltaValid(delta: Double) = !delta.isNaN() && delta.isFinite() && delta != 0.0

            fun encode(packet: Scroll, buf: FriendlyByteBuf): FriendlyByteBuf = buf.also {
                buf.writeDouble(packet.delta)
            }

            fun decode(buf: FriendlyByteBuf) = Scroll(
                buf.readDouble()
            )

            fun handle(packet: Scroll, ctx: Supplier<NetworkEvent.Context>) {
                ctx.get().enqueueWork {
                    if(!isDeltaValid(packet.delta)) {
                        return@enqueueWork
                    }

                    val sender = ctx.get().sender
                        ?: return@enqueueWork

                    val interactable = pickGameObject<ScrewdriverScrollable>(sender)
                        ?: return@enqueueWork

                    val interacted = interactable.first.scrollScrewdriver(sender, packet.delta)

                    if(!interacted) {
                        return@enqueueWork
                    }

                    sender.swing(InteractionHand.MAIN_HAND, true)

                    if(!cooldowns.containsKey(interactable)) {
                        sender.level().playSound(
                            null,
                            interactable.second.x, interactable.second.y, interactable.second.z,
                            SoundEvents.LAVA_POP,
                            SoundSource.BLOCKS,
                            Random.nextDouble(0.25, 0.3).toFloat(),
                            Random.nextDouble(3.0, 3.25).toFloat()
                        )

                        cooldowns[interactable] = COOLDOWN
                    }
                }
            }

            fun tickCooldowns() {
                cooldowns.toList().forEach {
                    val obj = it.first
                    val ticksLeft = it.second - 1

                    if(ticksLeft <= 0) {
                        cooldowns.remove(obj)
                    }
                    else {
                        cooldowns[obj] = ticksLeft
                    }
                }
            }
        }
    }

    data class SetConfigValue(val value: Double) {
        companion object {
            fun encode(packet: SetConfigValue, buf: FriendlyByteBuf): FriendlyByteBuf = buf.also {
                buf.writeDouble(packet.value)
            }

            fun decode(buf: FriendlyByteBuf) = SetConfigValue(
                buf.readDouble()
            )

            fun handle(packet: SetConfigValue, ctx: Supplier<NetworkEvent.Context>) {
                ctx.get().enqueueWork {
                    val sender = ctx.get().sender ?: return@enqueueWork

                    if(packet.value.isNaN() || packet.value.isInfinite()) {
                        return@enqueueWork
                    }

                    val stack = sender.mainHandItem

                    if(stack.item !is ScrewdriverItem) {
                        return@enqueueWork
                    }

                    setConfigValue(stack, packet.value)
                }
            }
        }
    }

    companion object {
        private const val CONFIG_VALUE = "configValue"

        private val CLEARED_MESSAGE = Component.translatable("item.eln2.screwdriver.config.cleared")

        fun getConfigValue(stack: ItemStack): OptionalDouble {
            val tag = stack.tag ?: return OptionalDouble.EMPTY
            return if(tag.contains(CONFIG_VALUE)) OptionalDouble.wrap(tag.getDouble(CONFIG_VALUE))
                else OptionalDouble.EMPTY
        }

        @ServerOnly
        fun setConfigValue(stack: ItemStack, value: Double) {
            stack.getOrCreateTag().putDouble(CONFIG_VALUE, value)
        }

        @ServerOnly
        fun clearConfigValue(stack: ItemStack) {
            stack.tag?.remove(CONFIG_VALUE)
        }
        private inline fun<reified T> pickGameObject(player: Player?) : Pair<T, Vector3d>? {
            val pick = org.eln2.mc.pickGameObject<T>(player) ?: return null
            return Pair(pick.target, pick.position)
        }

        fun onScroll(event: InputEvent.MouseScrollingEvent) {
            val player = Minecraft.getInstance().player
                ?: return

            if(player.mainHandItem.item != Eln2Tools.SCREWDRIVER.get()) {
                return
            }

            if(!player.isShiftKeyDown) {
                return
            }

            event.isCanceled = true

            if(Scroll.isDeltaValid(event.scrollDelta)) {
                Networking.sendToServer(Scroll(event.scrollDelta))
            }
        }
    }
}
/**
 * Mining modes for the [DrillItem].
 * */
enum class DrillMode {
    Single,
    Area3x3,
    Area5x5;

    fun next(): DrillMode = when (this) {
        Single -> Area3x3
        Area3x3 -> Area5x5
        Area5x5 -> Single
    }

    val radius: Int get() = when (this) {
        Single -> 0
        Area3x3 -> 1
        Area5x5 -> 2
    }
}

/**
 * Specification for the [DrillItem].
 *
 * @param baseSpeed The mining speed multiplier when powered (returned by [Item.getDestroySpeed]).
 * @param powerDemand The power drawn while actively mining.
 * @param areaPowerDemandMultiplier Multiplier applied to [powerDemand] in area modes (2x for both 3x3 and 5x5).
 * */
data class DrillModel(
    val baseSpeed: Float,
    val powerDemand: Quantity<Power>,
    val areaPowerDemandMultiplier: Double,
)

private const val DRILL_MODE = "drillMode"

/**
 * Per-player power consumer for the [DrillItem]. Created when mining starts, destroyed when mining stops.
 * Reports its demand to [PlayerPowerManager] each tick; the manager drains batteries accordingly.
 * Stores the mining [face] for area mining and the granted/demand fraction for speed modulation.
 * */
private class DrillPowerConsumer(
    private val player: ServerPlayer,
    private val drill: DrillItem,
) : InventoryPowerConsumer {
    override val priority = InventoryPowerPriority.Normal
    var face: Direction? = null

    var grantedThisTick = 0.0
        private set

    var demandThisTick = 0.0
        private set

    private var ticked = false
    var stopped = false
        private set

    override fun powerDemand(): Quantity<Power> {
        val stack = player.mainHandItem
        if (stack.item != drill) {
            return Quantity(0.0, WATT)
        }
        val mode = drill.getMode(stack)
        return when (mode) {
            DrillMode.Single -> drill.drillModel.powerDemand
            DrillMode.Area3x3, DrillMode.Area5x5 -> drill.drillModel.powerDemand * drill.drillModel.areaPowerDemandMultiplier
        }
    }

    override fun preTick() {
        grantedThisTick = 0.0
        demandThisTick = !powerDemand()
        ticked = true
    }

    override fun receivePower(granted: Quantity<Energy>): Quantity<Energy> {
        grantedThisTick += !granted
        return granted
    }

    override fun postTick(dt: Double) {
        fraction = if (!ticked || demandThisTick.approxEq(0.0)) {
            0.0f
        } else {
            (grantedThisTick / (demandThisTick * dt)).toFloat().coerceIn(0.0f, 1.0f)
        }
    }

    var fraction: Float = 0.0f
        private set

    fun markStopped() {
        stopped = true
    }
}

/**
 * An electrically-powered mining tool. Drains energy from [PowerCellItem]s in the player's inventory via [PlayerPowerManager].
 * The charge bar displays the highest-charge battery in the player's inventory.
 * Mining speed is modulated by the ratio of power granted by [PlayerPowerManager] to the drill's demand.
 * The fraction is computed server-side and synced to the client via [DrillPowerMessage].
 * With no batteries or all batteries depleted, the drill cannot mine.
 * In area modes, neighbors perpendicular to the clicked face are broken when the center block completes.
 * */
class DrillItem(val drillModel: DrillModel) : Item(Properties().stacksTo(1)) {
    fun getMode(stack: ItemStack): DrillMode {
        val tag = stack.tag ?: return DrillMode.Single
        val ordinal = tag.getInt(DRILL_MODE)
        return DrillMode.entries.getOrElse(ordinal) { DrillMode.Single }
    }

    fun setMode(stack: ItemStack, mode: DrillMode) {
        stack.getOrCreateTag().putInt(DRILL_MODE, mode.ordinal)
    }

    override fun useOn(pContext: UseOnContext): InteractionResult {
        val player = pContext.player ?: return InteractionResult.PASS

        if (player.isShiftKeyDown && !pContext.level.isClientSide) {
            val stack = pContext.itemInHand
            val mode = getMode(stack)
            val next = mode.next()
            setMode(stack, next)

            player.displayClientMessage(
                Component.translatable("item.eln2.drill.mode.$next.name"),
                true
            )

            return InteractionResult.SUCCESS
        }

        return InteractionResult.PASS
    }

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (pPlayer.isShiftKeyDown && !pLevel.isClientSide) {
            val stack = pPlayer.getItemInHand(pUsedHand)
            val mode = getMode(stack)
            val next = mode.next()
            setMode(stack, next)

            pPlayer.displayClientMessage(
                Component.translatable("item.eln2.drill.mode.$next.name"),
                true
            )

            return InteractionResultHolder.success(stack)
        }

        return super.use(pLevel, pPlayer, pUsedHand)
    }

    override fun getDestroySpeed(pStack: ItemStack, pState: BlockState): Float {
        return drillModel.baseSpeed
    }

    override fun isCorrectToolForDrops(pStack: ItemStack, pState: BlockState): Boolean {
        return true
    }

    override fun isBarVisible(pStack: ItemStack): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return findBestBatteryCharge(player) != null
    }

    override fun getBarWidth(pStack: ItemStack): Int {
        val player = Minecraft.getInstance().player ?: return 0
        val charge = findBestBatteryCharge(player) ?: return 0
        return (charge * 13.0).toInt().coerceIn(0, 13)
    }

    override fun getBarColor(pStack: ItemStack): Int {
        val player = Minecraft.getInstance().player ?: return 0xFFFFFF
        val charge = findBestBatteryCharge(player) ?: return 0xFFFFFF
        return when {
            charge < 0.2 -> 0xFF5555
            charge < 0.5 -> 0xFFAA00
            else -> 0x55FF55
        }
    }

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
            Component.translatable("tooltip.eln2.drill.power")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(drillModel.powerDemand)).withStyle(gray))
                .withStyle(yellow)
        )

        val mode = getMode(pStack)
        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.drill.mode")
                .append(": ")
                .append(Component.translatable("item.eln2.drill.mode.${mode.name.lowercase()}").withStyle(gray))
                .withStyle(yellow)
        )
    }

    companion object {
        private val consumers = HashMap<UUID, DrillPowerConsumer>()

        fun clearAll() {
            consumers.clear()
        }

        fun clearPlayer(uuid: UUID) {
            consumers.remove(uuid)
        }

        fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
            val player = event.entity as? ServerPlayer ?: return
            val stack = player.mainHandItem
            val drill = stack.item as? DrillItem ?: return

            when (event.action) {
                PlayerInteractEvent.LeftClickBlock.Action.START -> {
                    consumers.remove(player.uuid)?.let {
                        PlayerPowerManager.unregisterConsumer(player, it)
                    }
                    val consumer = DrillPowerConsumer(player, drill)
                    consumer.face = event.face
                    consumers[player.uuid] = consumer
                    PlayerPowerManager.registerConsumer(player, consumer)
                    Networking.send(DrillPowerMessage(0.0f), player)
                }

                PlayerInteractEvent.LeftClickBlock.Action.STOP -> {
                    consumers[player.uuid]?.markStopped()
                }

                PlayerInteractEvent.LeftClickBlock.Action.ABORT -> {
                    unregister(player)
                }

                else -> {}
            }
        }

        private fun unregister(player: ServerPlayer) {
            consumers.remove(player.uuid)?.let {
                PlayerPowerManager.unregisterConsumer(player, it)
            }
            Networking.send(DrillPowerMessage(0.0f), player)
        }

        fun onBreakSpeed(event: PlayerEvent.BreakSpeed) {
            val player = event.entity
            val stack = player.mainHandItem
            if (stack.item !is DrillItem) {
                return
            }

            val fraction = if (player.level().isClientSide) {
                DrillPowerMessage.clientFraction
            } else {
                consumers[player.uuid]?.fraction ?: 0.0f
            }

            if (fraction <= 0.0f) {
                event.isCanceled = true
                return
            }

            if (fraction < 1.0f) {
                event.newSpeed = event.originalSpeed * fraction
            }
        }

        fun onPlayerTickEnd(player: ServerPlayer) {
            val stack = player.mainHandItem
            val drill = stack.item as? DrillItem
            val consumer = consumers[player.uuid]

            if (drill == null || consumer == null) {
                if (consumer != null) {
                    unregister(player)
                }
                return
            }

            if (consumer.stopped) {
                unregister(player)
                return
            }

            Networking.send(DrillPowerMessage(consumer.fraction), player)
        }

        fun onBlockBreak(event: BlockEvent.BreakEvent) {

            val player = event.player as? ServerPlayer ?: return
            val stack = player.mainHandItem
            val drill = stack.item as? DrillItem ?: return

            val mode = drill.getMode(stack)
            if (mode != DrillMode.Single) {
                val consumer = consumers[player.uuid]
                val face = consumer?.face

                if (face != null) {
                    val level = event.level as? Level
                    if (level != null) {
                        breakArea(level, player, event.pos, face, mode.radius)
                    }
                }
            }

            unregister(player)
        }

        private fun breakArea(
            level: Level,
            player: ServerPlayer,
            center: BlockPos,
            face: Direction,
            radius: Int,
        ) {
            val positions = computeAreaPositions(center, face, radius)

            for (pos in positions) {
                if (pos == center) {
                    continue
                }

                val state = level.getBlockState(pos)

                if (state.isAir || state.getDestroySpeed(level, pos) < 0.0f) {
                    continue
                }

                level.destroyBlock(pos, true, player)
                player.causeFoodExhaustion(0.025f)
            }
        }

        internal fun computeAreaPositions(center: BlockPos, face: Direction, radius: Int): List<BlockPos> {
            val positions = ArrayList<BlockPos>()

            val (axisA, axisB) = when (face.axis) {
                Direction.Axis.Y -> Pair(Direction.Axis.X, Direction.Axis.Z)
                Direction.Axis.X -> Pair(Direction.Axis.Y, Direction.Axis.Z)
                Direction.Axis.Z -> Pair(Direction.Axis.X, Direction.Axis.Y)
            }

            for (a in -radius..radius) {
                for (b in -radius..radius) {
                    val offset = when {
                        axisA == Direction.Axis.X && axisB == Direction.Axis.Z -> BlockPos(a, 0, b)
                        axisA == Direction.Axis.Y && axisB == Direction.Axis.Z -> BlockPos(0, a, b)
                        axisA == Direction.Axis.X && axisB == Direction.Axis.Y -> BlockPos(a, b, 0)
                        else -> BlockPos.ZERO
                    }

                    positions.add(center + offset)
                }
            }

            return positions
        }

        /**
         * Picks the face of [center] that [player] is mining, derived from the player's eye position relative to the block center.
         * Only the axis matters for [computeAreaPositions], so the sign is irrelevant.
         * */
        internal fun pickMiningFace(center: BlockPos, player: Player): Direction {
            val eye = player.eyePosition
            return Direction.getNearest(
                eye.x - (center.x + 0.5),
                eye.y - (center.y + 0.5),
                eye.z - (center.z + 0.5)
            )
        }
    }
}

class DrillPowerMessage(val fraction: Float) {
    companion object {
        var clientFraction: Float = 0.0f
            private set

        fun reset() {
            clientFraction = 0.0f
        }

        fun encode(message: DrillPowerMessage, buf: FriendlyByteBuf) {
            buf.writeFloat(message.fraction)
        }

        fun decode(buf: FriendlyByteBuf) = DrillPowerMessage(buf.readFloat())

        fun handle(message: DrillPowerMessage, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                clientFraction = message.fraction
            }

            ctx.get().packetHandled = true
        }
    }
}
