package org.eln2.mc.extensions

import com.google.gson.JsonObject
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementRewards
import net.minecraft.advancements.RequirementsStrategy
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.SimpleBakedModel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.*
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.common.ForgeMod
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.network.NetworkHooks
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.mathematics.geometry.*
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.mathematics.Base6Direction3d
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.PI
import kotlin.math.cos

fun Advancement.Builder.eln2Unlock(recipeId: ResourceLocation) {
    this.parent(ResourceLocation.parse("minecraft:recipes/root"))
        .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(recipeId))
        .rewards(AdvancementRewards.Builder.recipe(recipeId))
        .requirements(RequirementsStrategy.OR)
}

val Block.blockID : ResourceLocation get() {
    val result = ForgeRegistries.BLOCKS.getKey(this)
        ?: error(DEBUGGER_BREAK("Could not resolve block $this"))

    return result
}

val Item.itemID : ResourceLocation get() {
    val result = ForgeRegistries.ITEMS.getKey(this)
        ?: error(DEBUGGER_BREAK("Could not resolve item $this"))

    return result
}

inline fun<reified T : Block> AbstractContainerMenu.eln2StillValid(pAccess: ContainerLevelAccess, pPlayer: Player) : Boolean {
    return pAccess.evaluate({ pLevel, pPos ->
        if(pLevel.getBlockState(pPos).block !is T) {
            return@evaluate false
        }

        return@evaluate pPlayer.distanceToSqr(pPos.x + 0.5, pPos.y + 0.5, pPos.z + 0.5) <= 64.0
    }, true)
}

fun eln2StandardBlockProperties(): BlockBehaviour.Properties = BlockBehaviour.Properties.of()
    .mapColor(MapColor.STONE)
    .instrument(NoteBlockInstrument.BASEDRUM)
    .strength(0.2f)

fun ItemStack.eln2Consume(pPlayer: Player, quantity: Int = 1) {
    if (!pPlayer.abilities.instabuild) {
        this.shrink(quantity)
    }
}

fun JsonObject.asFluidStack() : FluidStack {
    val fluidId = ResourceLocation.parse(this.get("fluid").asString)

    val fluid = ForgeRegistries.FLUIDS.getValue(fluidId)
        ?: error("Invalid fluid \"$fluidId\"")

    val amount = this.get("amount").asInt

    return FluidStack(fluid, amount)
}

fun ItemStack.takeDurability(player: Player, hand: InteractionHand = InteractionHand.MAIN_HAND, durability: Int = 1) {
    this.hurtAndBreak(durability, player, { p -> p.broadcastBreakEvent(hand) })
}

fun BlockEntity.setSyncDirty() {
    val level = level
        ?: return

    if(level.isClientSide) {
        return
    }

    level.sendBlockUpdated(
        blockPos,
        blockState,
        blockState,
        Block.UPDATE_CLIENTS
    )
}

@OptIn(ExperimentalContracts::class)
inline fun PoseStack.preserve(block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    this.pushPose()
    block()
    this.popPose()
}

fun Entity.getClipStartEnd() : Pair<Vec3, Vec3> {
    val viewDirection = this.lookAngle

    val start = Vec3(this.x, this.eyeY, this.z)

    val distance = if(this is Player) {
        this.blockReach
    }
    else {
        ForgeMod.BLOCK_REACH.get().defaultValue
    }

    val end = start + viewDirection * distance

    return Pair(start, end)
}

fun Entity.getViewRay(): Ray3d {
    val lookAngle = this.lookAngle
    val viewDirection = Vector3d(lookAngle.x, lookAngle.y, lookAngle.z)
    val start = Vector3d(this.x, this.eyeY, this.z)

    return Ray3d(start, viewDirection.normalized())
}

fun Entity.getViewLine(): Line3d {
    val ray = this.getViewRay()
    val (a, b) = this.getClipStartEnd()

    return Line3d(ray.origin, ray.direction, a.distanceTo(b))
}

fun AABB.viewClip(entity: LivingEntity): Optional<Vec3> {
    val (start, end) = entity.getClipStartEnd()

    return this.clip(start, end)
}

fun AABB.viewClipExtra(entity: LivingEntity, blockPos: BlockPos) : BlockHitResult? {
    val (start, end) = entity.getClipStartEnd()
    return AABB.clip(listOf(this), start, end, blockPos)
}

fun AABB.minVec3(): Vec3 {
    return Vec3(this.minX, this.minY, this.minZ)
}

fun AABB.maxVec3(): Vec3 {
    return Vec3(this.maxX, this.maxY, this.maxZ)
}

fun AABB.size(): Vec3 {
    return this.maxVec3() - this.minVec3()
}

fun AABB.corners(list: MutableList<Vec3>) {
    val min = this.minVec3()
    val max = this.maxVec3()

    list.add(min)
    list.add(Vec3(min.x, min.y, max.z))
    list.add(Vec3(min.x, max.y, min.z))
    list.add(Vec3(max.x, min.y, min.z))
    list.add(Vec3(min.x, max.y, max.z))
    list.add(Vec3(max.x, min.y, max.z))
    list.add(Vec3(max.x, max.y, min.z))
    list.add(max)
}

fun AABB.corners(): ArrayList<Vec3> {
    val list = ArrayList<Vec3>()

    this.corners(list)

    return list
}

/**
 * Transforms the Axis Aligned Bounding Box by the given rotation.
 * This operation does not change the volume for axis aligned transformations.
 * */
fun AABB.transformed(quaternion: Quaternionfc): AABB {
    var min = Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    var max = Vector3f(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    this.corners().forEach {
        val corner = quaternion.transform(it.toJoml())

        min = componentMin(min, corner)
        max = componentMax(max, corner)
    }

    return AABB(min.toVec3(), max.toVec3())
}

/**
 * Rotates an [AABB] around [pivot] by [rotation], returning the new axis-aligned bounding box.
 * */
fun AABB.rotatedAround(pivot: Vector3f, rotation: Quaternionf): AABB {
    var min = Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    var max = Vector3f(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    this.corners().forEach { corner ->
        val point = corner.toJoml()
        point.sub(pivot)
        rotation.transform(point)
        point.add(pivot)

        min = componentMin(min, point)
        max = componentMax(max, point)
    }

    return AABB(min.toVec3(), max.toVec3())
}

fun AABB.size3d() = Vector3d(this.maxX - this.minX, this.maxY - this.minY, this.maxZ - this.minZ)
fun BlockState.facing(): Direction = this.getValue(HorizontalDirectionalBlock.FACING)
operator fun BlockPos.plus(displacement: Vec3i): BlockPos {
    return this.offset(displacement)
}

operator fun BlockPos.plus(direction: Direction): BlockPos {
    return this + direction.normal
}

operator fun BlockPos.minus(displacement: Vec3i): BlockPos {
    return this.subtract(displacement)
}

operator fun BlockPos.minus(other: BlockPos): BlockPos {
    return BlockPos(this.x - other.x, this.y - other.y, this.z - other.z)
}

operator fun BlockPos.minus(direction: Direction): BlockPos {
    return this - direction.normal
}

fun BlockPos.directionTo(other: BlockPos) = directionByNormal(other - this)
fun Direction.isVertical(): Boolean {
    return this == Direction.UP || this == Direction.DOWN
}

fun Direction.isHorizontal(): Boolean {
    return !isVertical()
}

val Direction.alias: Base6Direction3d
    get() = when (this) {
        Direction.DOWN -> Base6Direction3d.Down
        Direction.UP -> Base6Direction3d.Up
        Direction.NORTH -> Base6Direction3d.Front
        Direction.SOUTH -> Base6Direction3d.Back
        Direction.WEST -> Base6Direction3d.Left
        Direction.EAST -> Base6Direction3d.Right
    }

private val DIRECTION_TO_VECTOR3D = buildDirectionTable {
    Vector3d(it.stepX.toDouble(), it.stepY.toDouble(), it.stepZ.toDouble())
}

val Direction.vector3d get() = DIRECTION_TO_VECTOR3D[this.get3DDataValue()]

private val DIRECTION_TO_JOML_QUATERNION = buildDirectionTable {
    it.rotation
}

val Direction.rotationFast: Quaternionf get() = DIRECTION_TO_JOML_QUATERNION[this.get3DDataValue()]

private val DIRECTION_TO_ROTATION3D = buildDirectionTable {
    it.rotation.cast()
}

val Direction.rotation3d get() = DIRECTION_TO_ROTATION3D[this.get3DDataValue()]

fun evaluateSunlight(rainLevel: Double, thunderLevel: Double, timeOfDay: Double) : Double {
    val d0 = 1.0 - rainLevel * 5.0 / 16.0
    val d1 = 1.0 - thunderLevel * 5.0 / 16.0
    val d2 = 0.5 + 2.0 * cos((2.0 * PI) * timeOfDay).coerceIn(-0.25, 0.25) // We are also compounding this with our own calculation. Maybe remove this?

    return (d0 * d1 * d2).coerceIn(0.0, 1.0)
}

fun Level.evaluateSunlight() = evaluateSunlight(
    this.getRainLevel(1.0f).toDouble(),
    this.getThunderLevel(1.0f).toDouble(),
    this.getTimeOfDay(1.0f).toDouble()
)

fun Level.evaluateDiffuseIrradianceFactor(normal: Vector3d) : Double {
    val pass = this.celestialPass()
    val sunlight = this.evaluateSunlight()

    val u = !Vector3d(pass.re, pass.im, 0.0) cosAngle !normal

    return if(u >= 0.0) {
        sunlight * u
    }
    else {
        0.0
    }
}

fun Level.evaluateDiffuseIrradianceFactor(normal: Vector3d, blockPos: BlockPos) : Double {
    if(!this.canSeeSky(blockPos)) {
        return 0.0
    }

    return this.evaluateDiffuseIrradianceFactor(normal)
}

fun Level.playLocalSound(
    pos: Vec3,
    pSound: SoundEvent,
    pCategory: SoundSource,
    pVolume: Float,
    pPitch: Float,
    pDistanceDelay: Boolean,
) {
    this.playLocalSound(pos.x, pos.y, pos.z, pSound, pCategory, pVolume, pPitch, pDistanceDelay)
}

fun Level.addParticle(
    pParticleData: ParticleOptions,
    pos: Vec3,
    pXSpeed: Double,
    pYSpeed: Double,
    pZSpeed: Double,
) {
    this.addParticle(pParticleData, pos.x, pos.y, pos.z, pXSpeed, pYSpeed, pZSpeed)
}

fun Level.addParticle(
    pParticleData: ParticleOptions,
    pos: Vec3,
    speed: Vec3,
) {
    this.addParticle(pParticleData, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z)
}

fun ServerLevel.addItem(x: Double, y: Double, z: Double, stack: ItemStack) {
    if(this.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS) && !this.restoringBlockSnapshots) {
        val entity =  ItemEntity(
            this,
            x,
            y,
            z,
            stack
        )

        entity.setDefaultPickUpDelay()

        this.addFreshEntity(entity)
    }
}

fun ServerLevel.addItem(pos: BlockPos, stack: ItemStack) = addItem(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), stack)

/**
 * Adds an item to the player's inventory, falling back to dropping it in the world if not possible.
 * */
@ServerOnly
fun ServerPlayer.addItem(x: Double, y: Double, z: Double, stack: ItemStack) {
    requireIsOnServerThread {
        "Cannot call org.eln2.mc.extensions.MinecraftExtensionsKt.addItem(net.minecraft.server.level.ServerPlayer, double, double, double, net.minecraft.world.item.ItemStack) on non-server thread"
    }

    if(!this.addItem(stack)) {
        (this.level() as ServerLevel).addItem(x, y, z, stack)
    }
}

/**
 * Adds an item to the player's inventory, falling back to dropping it in the world if not possible.
 * */
@ServerOnly
fun ServerPlayer.addItem(fallbackPosition: Vector3d, stack: ItemStack) {
    this.addItem(fallbackPosition.x, fallbackPosition.y, fallbackPosition.z, stack)
}

@ServerOnly
fun ServerLevel.destroyPart(part: Part, dropPart: Boolean) {
    val pos = part.placement.position

    val multipart = this.getBlockEntity(pos)
        as? MultipartBlockEntity

    if (multipart == null) {
        LOG.error("Multipart null at $pos")

        return
    }

    val saveTag = CompoundTag()

    multipart.breakPart(part, saveTag)

    if(dropPart) {
        val itemEntity = ItemEntity(
            this,
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            Part.createPartDropStack(part.id, saveTag)
        )

        this.addFreshEntity(itemEntity)
    }

    if (multipart.isEmpty) {
        this.destroyBlock(pos, false)
    }
}

inline fun <reified TBlockEntity : BlockEntity> Level.constructMenuHelper(
    pos: BlockPos,
    player: Player,
    title: Component,
    crossinline factory: (
        pBlockEntity: TBlockEntity,
        pContainerId: Int,
        pPlayerInventory: Inventory,
        pPlayer: Player,
    ) -> AbstractContainerMenu,
): InteractionResult {

    if (this.isClientSide) {
        return InteractionResult.SUCCESS
    }

    val entity = this.getBlockEntity(pos) as? TBlockEntity
        ?: return InteractionResult.FAIL

    val containerProvider = object : MenuProvider {
        override fun getDisplayName() = title

        override fun createMenu(
            pContainerId: Int,
            pInventory: Inventory,
            pPlayer: Player,
        ): AbstractContainerMenu = factory(
            entity,
            pContainerId,
            pInventory,
            pPlayer
        )
    }

    NetworkHooks.openScreen(player as ServerPlayer, containerProvider, entity.blockPos)

    return InteractionResult.SUCCESS
}

inline fun <reified TBlockEntity : BlockEntity> Level.constructMenuHelper2(
    pos: BlockPos,
    player: Player,
    title: Component,
    crossinline factory: (
        pBlockEntity: TBlockEntity,
        pContainerId: Int,
        pPlayerInventory: Inventory,
    ) -> AbstractContainerMenu,
) = this.constructMenuHelper<TBlockEntity>(pos, player, title) { pBlockEntity: TBlockEntity, pContainerId: Int, pPlayerInventory: Inventory, _: Player ->
    factory(pBlockEntity, pContainerId, pPlayerInventory)
}

fun Vector3f.toVec3() = Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())

val Base6Direction3d.alias: Direction
    get() = when (this) {
        Base6Direction3d.Front -> Direction.NORTH
        Base6Direction3d.Back -> Direction.SOUTH
        Base6Direction3d.Left -> Direction.WEST
        Base6Direction3d.Right -> Direction.EAST
        Base6Direction3d.Up -> Direction.UP
        Base6Direction3d.Down -> Direction.DOWN
    }

val Base6Direction3d.data3D: Int
    get() = this.alias.get3DDataValue()

fun Rotation.inverse() = when (this) {
    Rotation.NONE -> Rotation.NONE
    Rotation.CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90
    Rotation.CLOCKWISE_180 -> Rotation.CLOCKWISE_180
    Rotation.COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90
}

operator fun Rotation.times(p: BlockPos): BlockPos = p.rotate(this)

operator fun Vec3.plus(b: Vec3): Vec3 {
    return Vec3(this.x + b.x, this.y + b.y, this.z + b.z)
}

operator fun Vec3.plus(delta: Double): Vec3 {
    return Vec3(this.x + delta, this.y + delta, this.z + delta)
}

operator fun Vec3.minus(b: Vec3): Vec3 {
    return Vec3(this.x - b.x, this.y - b.y, this.z - b.z)
}

operator fun Vec3.minus(delta: Double): Vec3 {
    return Vec3(this.x - delta, this.y - delta, this.z - delta)
}

operator fun Vec3.times(b: Vec3): Vec3 {
    return Vec3(this.x * b.x, this.y * b.y, this.z * b.z)
}

operator fun Vec3.times(scalar: Double): Vec3 {
    return Vec3(this.x * scalar, this.y * scalar, this.z * scalar)
}

operator fun Vec3.div(b: Vec3): Vec3 {
    return Vec3(this.x / b.x, this.y / b.y, this.z / b.z)
}

operator fun Vec3.div(scalar: Double): Vec3 {
    return Vec3(this.x / scalar, this.y / scalar, this.z / scalar)
}

operator fun Vec3.unaryMinus(): Vec3 {
    return Vec3(-this.x, -this.y, -this.z)
}

operator fun Vec3.unaryPlus(): Vec3 {
    // For completeness

    return Vec3(this.x, this.y, this.z)
}

fun Vec3i.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun BlockPos.toVector3d() = Vector3d(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
fun BlockPos.toVector3di() = Vector3di(this.x, this.y, this.z)
fun BlockEntity.sendClientUpdate() =
    this.level!!.sendBlockUpdated(this.blockPos, this.blockState, this.blockState, Block.UPDATE_CLIENTS)

fun Entity.moveTo(v: Vector3d) = this.moveTo(v.x, v.y, v.z)
fun Vector3d.toVec3() = Vec3(this.x, this.y, this.z)
fun Direction.valueHashCode() = this.normal.hashCode()
fun Vector3di.toBlockPos() = BlockPos(this.x, this.y, this.z)
fun ChunkPos.toVector2d() = Vector2d(this.x.toDouble(), this.z.toDouble())
fun Vec3.toJoml() = Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
fun BakedQuad.bind() = BakedQuad(
    this.vertices.copyOf(),
    this.tintIndex,
    this.direction,
    this.sprite,
    this.isShade,
    this.hasAmbientOcclusion()
)

@Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
fun BakedModel.bind() : SimpleBakedModel {
    val quads = HashMap<BakedQuad, BakedQuad>()

    return SimpleBakedModel(
        this.getQuads(null, null, null)
            .map { quad ->
                quad.bind().also { boundQuad ->
                    quads[quad] = boundQuad
                }
            },
        let {
            val cull = HashMap<Direction, List<BakedQuad>>()

            Direction.values().forEach { dir ->
                cull[dir] = this.getQuads(null, dir, null).map {
                    quads[it]!!
                }
            }

            cull
        },
        this.useAmbientOcclusion(),
        this.usesBlockLight(),
        this.isGui3d,
        this.particleIcon,
        this.transforms,
        this.overrides
    )
}

fun RandomSource.nextDouble(min: Double, max: Double) = Mth.nextDouble(this, min, max)

/**
 * Gets the celestial phase from the in-game celestial angle.
 * @param sunAngle The celestial angle, as per [Level.getSunAngle]
 * @return A [Rotation2d] that represents the current celestial pass. The real axis is fixed as the ground. When the value is in the upper semicircle, the sun is passing. When the value is in the lower semicircle, the moon is passing.
 * */
fun celestialPass(sunAngle: Double) = Rotation2d.exp(sunAngle + PI / 2.0)

/**
 * Gets the deviation between the normal and the direction towards the celestial body.
 * @return The deviation angle. It is always positive, no matter if the celestial body is the sun or the moon.
 * */
fun celestialDeviation(sunAngle: Double, normal: Vector3d) : Double {
    var pass = celestialPass(sunAngle)

    if(pass.im < 0.0) {
        // Night
        pass = !pass
    }

    return !Vector3d(pass.re, pass.im, 0.0) angle !normal
}

fun Level.celestialPass() = celestialPass(this.getSunAngle(1.0f).toDouble())
fun Level.celestialDeviation(normal: Vector3d) = celestialDeviation(this.getSunAngle(1.0f).toDouble(), normal)
fun VoxelShape.toBoxList() : List<AABB> {
    val results = ArrayList<AABB>(2)

    this.forAllBoxes { pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ ->
        results.add(
            AABB(
                pMinX, pMinY, pMinZ,
                pMaxX, pMaxY, pMaxZ
            )
        )
    }

    return results
}

fun Double.formatted(decimals: Int = 2): String {
    return "%.${decimals}f".format(this)
}

fun <U> CompoundTag.putQuantity(key: String, e: Quantity<U>) = this.putDouble(key, !e)
fun <U> CompoundTag.getQuantity(key: String) = Quantity<U>(this.getDouble(key))
inline fun ListTag.forEachCompound(action: (CompoundTag) -> Unit) {
    for (tag in this) {
        val compound = checkNotNull(tag as? CompoundTag) {
            "Failed to cast element in list tag to compound"
        }

        action(compound)
    }
}

fun CompoundTag.getListTag(key: String) : ListTag = checkNotNull(this.get(key) as? ListTag) {
    "Failed to get list tag from compound"
}

fun Rotation3d.cast() = Quaternionf().set(this.x.toFloat(), this.y.toFloat(), this.z.toFloat(), this.w.toFloat())

fun Vector3d.cast() = Vector3f().set(this.x, this.y, this.z)

fun Quaternionf.cast() = Rotation3d(this.x.toDouble(), this.y.toDouble(), this.z.toDouble(), this.w.toDouble())

fun BoundingBox3d.cast() = AABB(this.min.toVec3(), this.max.toVec3())

fun AABB.cast() = BoundingBox3d(Vector3d(minX, minY, minZ), Vector3d(maxX, maxY, maxZ))

fun Vec3.cast() = Vector3d(this.x, this.y, this.z)

fun PoseStack.mulPose(r: Rotation3d) {
    this.mulPose(r.cast())
}

fun PoseStack.translate(t: Vector3d) {
    this.translate(t.x, t.y, t.z)
}

fun FriendlyByteBuf.writeVector2d(vector2d: Vector2d) {
    this.writeDouble(vector2d.x)
    this.writeDouble(vector2d.y)
}

fun FriendlyByteBuf.readVector2d() = Vector2d(
    this.readDouble(),
    this.readDouble()
)

fun FriendlyByteBuf.writeRotation2d(rotation2d: Rotation2d) {
    this.writeDouble(rotation2d.re)
    this.writeDouble(rotation2d.im)
}

fun FriendlyByteBuf.readRotation2d() = Rotation2d(
    this.readDouble(),
    this.readDouble()
)

fun FriendlyByteBuf.writePose2d(pose2d: Pose2d) {
    this.writeVector2d(pose2d.translation)
    this.writeRotation2d(pose2d.rotation)
}

fun FriendlyByteBuf.readPose2d() = Pose2d(
    this.readVector2d(),
    this.readRotation2d()
)

fun Level.recipeExists(recipe: RecipeType<*>, stack: ItemStack) : Boolean {
    val recipeManager = this.recipeManager

    @Suppress("UNCHECKED_CAST") val recipe = recipeManager.getRecipeFor(
        recipe as RecipeType<Recipe<Container>>,
        SimpleContainer(stack),
        this
    )

    return recipe.isPresent
}

fun ItemStackHandler.bind() : ItemStackHandler {
    val result = ItemStackHandler(this.slots)

    for (slot in 0 until this.slots) {
        result.setStackInSlot(slot, this.getStackInSlot(slot).copy())
    }

    return result
}

fun ItemStackHandler.bindToSimpleContainer() : SimpleContainer {
    val copy = SimpleContainer(this.slots)

    repeat(this.slots) {
        copy.setItem(it, this.getStackInSlot(it).copy())
    }

    return copy
}

fun SimpleContainer.and(rangeInclude: IntRange) {
    for (slot in 0 until this.containerSize) {
        if (!rangeInclude.contains(slot)) {
            this.setItem(slot, ItemStack.EMPTY)
        }
    }
}

fun SimpleContainer.bind() : SimpleContainer {
    val result = SimpleContainer(this.containerSize)

    for (slot in 0 until this.containerSize) {
        result.setItem(slot, this.getItem(slot).copy())
    }

    return result
}

fun SimpleContainer.bindToList() : ArrayList<ItemStack> {
    val result = ArrayList<ItemStack>()

    for (slot in 0 until this.containerSize) {
        val stack = this.getItem(slot)

        if(!stack.isEmpty) {
            result.add(stack.copy())
        }
    }

    return result
}

fun Level.recipeExists(recipe: RecipeType<*>, container: SimpleContainer) : Boolean {
    val recipeManager = this.recipeManager

    @Suppress("UNCHECKED_CAST") val recipe = recipeManager.getRecipeFor(
        recipe as RecipeType<Recipe<Container>>,
        container,
        this
    )

    return recipe.isPresent
}
