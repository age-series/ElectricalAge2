package org.eln2.mc.common.blocks.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.*
import org.eln2.mc.*
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.Base6Direction3dMask
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.HashSet
import kotlin.collections.set

class MultipartBlock : BaseEntityBlock(
    Properties.of(Material.STONE)
    .noOcclusion()
    .destroyTime(0.2f)) {

    private val epsilon = 0.00001
    private val emptyBox = box(0.0, 0.0, 0.0, epsilon, epsilon, epsilon)

    //#region Block Methods

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return MultipartBlockEntity(pPos, pState)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("true"))
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean {
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape {
        return getMultipartShape(pLevel, pPos)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape {
        return getPartShape(pLevel, pPos, pContext)
    }

    @Deprecated("Deprecated in Java")
    override fun getVisualShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape {
        return getPartShape(pLevel, pPos, pContext)
    }

    override fun onDestroyedByPlayer(
        state: BlockState?,
        level: Level?,
        pos: BlockPos?,
        player: Player?,
        willHarvest: Boolean,
        fluid: FluidState?,
    ): Boolean {
        if (pos == null) {
            return false
        }

        if (level !is ServerLevel) {
            return false
        }

        if (player == null) {
            return false
        }

        val multipart = level.getBlockEntity(pos) as? MultipartBlockEntity

        if (multipart == null) {
            LOG.error("Multipart null at $pos")
            return false
        }

        val saveTag = CompoundTag()

        val removedId = multipart.remove(player, level, saveTag)
            ?: return false

        if (!player.isCreative) {
            player.inventory.add(Part.createPartDropStack(removedId, saveTag))
        }

        // We want to destroy the multipart only if it is empty
        val multipartIsDestroyed = multipart.isEmpty

        if (multipartIsDestroyed) {
            // There is an edge case here!
            // Because we destroyed it, the update packet never got sent, unfortunately.
            // We will manually send the update packet:

            val chunk = level.chunkSource.chunkMap.updatingChunkMap.get(ChunkPos(pos).toLong())

            if(chunk == null) {
                LOG.error("Failed to access chunk holder for removed multipart!")
            }
            else {
                // Force update packet:
                chunk.broadcastBlockEntity(level, pos)
                level.destroyBlock(pos, false)
            }
        }

        return multipartIsDestroyed
    }

    override fun addRunningEffects(state: BlockState?, level: Level?, pos: BlockPos?, entity: Entity?): Boolean {
        return true
    }

    override fun addLandingEffects(
        state1: BlockState?,
        level: ServerLevel?,
        pos: BlockPos?,
        state2: BlockState?,
        entity: LivingEntity?,
        numberOfParticles: Int,
    ): Boolean {
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun neighborChanged(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pBlock: Block,
        pFromPos: BlockPos,
        pIsMoving: Boolean,
    ) {
        val multipart = pLevel.getBlockEntity(pPos) as? MultipartBlockEntity ?: return

        val completelyDestroyed = multipart.onNeighborDestroyed(pFromPos)

        if (completelyDestroyed) {
            pLevel.destroyBlock(pPos, false)
        }

        super.neighborChanged(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving)
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        val multipart = pLevel
            .getBlockEntity(pPos) as? MultipartBlockEntity
            ?: return InteractionResult.FAIL

        return multipart.use(pPlayer, pHand)
    }

    //#endregion

    private fun getMultipartShape(level: BlockGetter, pos: BlockPos): VoxelShape {
        val multipart = level.getBlockEntity(pos) as? MultipartBlockEntity ?: return emptyBox

        return multipart.collisionShape
    }

    private fun getPartShape(level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val pickedPart = pickPart(level, pos, context)
            ?: return emptyBox

        return pickedPart.modelShape
    }

    private fun pickPart(level: BlockGetter, pos: BlockPos, context: CollisionContext): Part<*>? {
        if (context !is EntityCollisionContext) {
            LOG.error("Collision context was not an entity collision context at $pos")
            return null
        }

        val entity: LivingEntity

        if (context.entity == null) {
            if (level is ClientLevel && Minecraft.getInstance() != null && Minecraft.getInstance().player != null) {
                // What to do?
                // It doesn't give me the context
                // Hopefully, this workaround won't screw me
                entity = Minecraft.getInstance().player!!
            } else {
                return null
            }
        } else {
            if (context.entity is LivingEntity) {
                entity = context.entity as LivingEntity
            } else {
                return null
            }
        }

        return pickPart(level, pos, entity)
    }

    private fun pickPart(level: BlockGetter, pos: BlockPos, entity: LivingEntity): Part<*>? {
        val multipart = level.getBlockEntity(pos)

        if (multipart == null) {
            LOG.error("Multipart block failed to get entity at $pos")
            return null
        }

        if (multipart !is MultipartBlockEntity) {
            LOG.error("Multipart block found other entity type at $pos")
            return null
        }

        return multipart.pickPart(entity)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {

        return createTickerHelper(
            pBlockEntityType,
            BlockRegistry.MULTIPART_BLOCK_ENTITY.get(),
            MultipartBlockEntity.Companion::serverTick
        )
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        val entity = pLevel.getBlockEntity(pPos) as? MultipartBlockEntity

        entity?.animateTick(pRandom)
    }

    override fun getCloneItemStack(
        state: BlockState?,
        target: HitResult?,
        level: BlockGetter?,
        pos: BlockPos?,
        player: Player?,
    ): ItemStack {
        if (level == null || pos == null || player == null) {
            return ItemStack.EMPTY
        }

        val picked = pickPart(level, pos, player)
            ?: return ItemStack.EMPTY

        return ItemStack(PartRegistry.getPartItem(picked.id))
    }
}

/**
 * Multipart entities
 *  - Are dummy entities, that do not have any special data or logic by themselves
 *  - Act as a container for Parts. There may be one part per inner face (maximum of 6 parts per multipart entity)
 *  - The player can place inside the multipart entity. Placement and breaking logic must be emulated.
 *  - The multipart entity saves data for all the parts. Parts are responsible for their rendering.
 *  - A part can also be included in the simulation. Special connection logic is implemented for CellPart.
 * */
class MultipartBlockEntity(var pos: BlockPos, state: BlockState) :
    BlockEntity(BlockRegistry.MULTIPART_BLOCK_ENTITY.get(), pos, state),
    CellContainer,
    WailaNode {

    // Interesting issue.
    // If we try to add tickers before the block receives the first tick,
    // we will cause some deadlock in Minecraft's code.
    // This is set to TRUE when this block first ticks. We only update the ticker if this is set to true.
    private var worldLoaded = false

    private val parts = HashMap<Direction, Part<*>>()

    // Used for part sync:
    private val dirtyParts = HashSet<Direction>()
    private val placementUpdates = ArrayList<PartUpdate>()

    val hasPlacementUpdates get() = placementUpdates.isNotEmpty()

    // Used for disk loading:
    private var savedTag: CompoundTag? = null

    private var tickingParts = HashSet<TickablePart>()
    private var animatedParts = HashSet<AnimatedPart>()

    // This is useful, because a part might remove its ticker whilst being ticked
    // (which would cause our issues with iteration)
    private var tickingRemoveQueue = HashSet<TickablePart>()
    private var animationRemoveQueue = HashSet<AnimatedPart>()

    val isEmpty get() = parts.isEmpty()

    // Used for rendering:
    @ClientOnly
    val renderUpdates = ConcurrentLinkedQueue<PartUpdate>()

    var collisionShape: VoxelShape private set

    init {
        collisionShape = Shapes.empty()
    }

    fun getPart(face: Direction): Part<*>? {
        return parts[face]
    }

    private fun destroyPart(face: Direction): Part<*>? {
        val result = parts.remove(face)
            ?: return null

        tickingParts.removeIf { it == result }

        if (level?.isClientSide != false) {
            animatedParts.removeIf { it == result }
        }

        result.onRemoved()

        return result
    }

    private fun addPart(face: Direction, part: Part<*>) {
        parts[face] = part
        part.onAdded()
    }

    /**
     * Enqueues this multipart for synchronization to clients.
     * */
    @ServerOnly
    private fun setSyncDirty() {
        level!!.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /**
     * Enqueues a part for synchronization to clients.
     * This is used to synchronize custom part data.
     * */
    @ServerOnly
    fun enqueuePartSync(face: Direction) {
        dirtyParts.add(face)
        setSyncDirty() // TODO: Can we batch multiple updates?
    }

    /**
     * Finds the part intersected by the entity's view.
     * */
    fun pickPart(entity: LivingEntity): Part<*>? {
        return clipScene(
            entity,
            { it.first },
            parts.values.flatMap { part ->
                part.worldShapeParts.map { aabb ->
                    Pair(aabb, part)
                }
            }
        )?.second
    }

    /**
     * Attempts to place a part.
     * @return True if the part was successfully placed. Otherwise, false.
     * */
    @ServerOnly
    fun place(
        entity: Player,
        pos: BlockPos,
        face: Direction,
        provider: PartProvider,
        saveTag: CompoundTag? = null,
        orientation: Direction? = null,
    ): Boolean {
        if (orientation != null && !orientation.isHorizontal()) {
            error("Invalid orientation $orientation")
        }

        if (entity.level.isClientSide) {
            return false
        }

        val level = entity.level as ServerLevel

        if (parts.containsKey(face)) {
            return false
        }

        val neighborPos = pos - face
        val targetBlockState = level.getBlockState(neighborPos)

        if (!targetBlockState.isCollisionShapeFullBlock(level, neighborPos)) {
            LOG.info("Cannot place on non-full block")
            return false
        }

        val placeDirection = orientation ?: if (face.isVertical()) {
            entity.direction
        } else {
            Direction.NORTH
        }

        val placementContext = PartPlacementInfo(pos, face, placeDirection, level, this, provider)

        val worldBoundingBox = PartGeometry.worldBoundingBox(
            provider.placementCollisionSize,
            placementContext.horizontalFacing,
            placementContext.face,
            placementContext.position
        )

        val collides = parts.values.any { part ->
            part.worldBoundingBox.intersects(worldBoundingBox)
        }

        if (collides) {
            return false
        }

        if (!provider.canPlace(placementContext)) {
            return false
        }

        val part = provider.create(placementContext)

        addPart(face, part)

        placementUpdates.add(PartUpdate(part, PartUpdateType.Add))
        joinCollider(part)

        if (part is ItemPersistentPart && part.order == ItemPersistentPartLoadOrder.BeforeSim) {
            part.loadFromItemNbt(saveTag)
        }

        part.onPlaced()

        if (part is PartCellContainer<*>) {
            CellConnections.insertFresh(this, part.cell)
        }

        if (part is ItemPersistentPart && part.order == ItemPersistentPartLoadOrder.AfterSim) {
            part.loadFromItemNbt(saveTag)
        }

        if (part is PartCellContainer<*>) {
            part.cell.bindGameObjects(listOf(this, part))
        }

        setChanged()
        setSyncDirty()

        return true
    }

    /**
     * Tries to destroy a part.
     * @param saveTag A tag to save part data, if required.
     * @return The ID of the part that was broken, if any were picked. Otherwise, null.
     * */
    @ServerOnly
    fun remove(entity: Player, level: Level, saveTag: CompoundTag? = null): ResourceLocation? {
        if (level.isClientSide) {
            return null
        }

        val part = pickPart(entity)
            ?: return null

        val id = part.id

        breakPart(part, saveTag)

        return id
    }

    /**
     * Destroys a part, saves and synchronizes the changes.
     * */
    @ServerOnly
    fun breakPart(part: Part<*>, saveTag: CompoundTag? = null) {
        if (part is PartCellContainer<*>) {
            part.cell.unbindGameObjects()
            CellConnections.destroy(part.cell, this)
        }

        if (part is ItemPersistentPart && saveTag != null) {
            part.saveToItemNbt(saveTag)
        }

        destroyPart(part.placement.face)
        placementUpdates.add(PartUpdate(part, PartUpdateType.Remove))

        part.onBroken()

        setChanged()
        setSyncDirty()
    }

    /**
     * Called by the block when a neighbor is destroyed.
     * If a part is placed on the face corresponding to that neighbor,
     * the part must be destroyed.
     * */
    @ServerOnly
    fun onNeighborDestroyed(neighborPos: BlockPos): Boolean {
        if (level!!.isClientSide) {
            return false
        }

        val direction = neighborPos.directionTo(pos)

        if (direction == null) {
            LOG.error("Failed to get direction")
            return false
        } else {
            LOG.info("Face: $direction")
        }

        if (parts.containsKey(direction)) {
            breakPart(parts[direction]!!)
        }

        return parts.size == 0
    }

    /**
     * Merges the current multipart collider with the collider of the part.
     * */
    private fun joinCollider(part: Part<*>) {
        collisionShape = Shapes.join(collisionShape, part.modelShape, BooleanOp.OR)
    }

    /**
     * Builds the collider from the current parts.
     * */
    fun rebuildCollider() {
        collisionShape = Shapes.empty()

        parts.values.forEach { part ->
            collisionShape = Shapes.joinUnoptimized(collisionShape, part.modelShape, BooleanOp.OR)
        }

        collisionShape.optimize()
    }

    //#region Client Chunk Synchronization

    // The following methods get called when chunks are first synchronized to clients
    // Here, we send all the parts we have.

    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        if (level!!.isClientSide) {
            return CompoundTag() //?
        }

        val tag = saveParts(true)

        parts.values.forEach {
            it.onSyncSuggested()
        }

        return tag
    }

    @ClientOnly
    override fun handleUpdateTag(tag: CompoundTag?) {
        if (tag == null) {
            LOG.error("Part update tag was null at $pos")
            return
        }

        if (level == null) {
            LOG.error("Level was null in handleUpdateTag at $pos")
            return
        }

        if (!level!!.isClientSide) {
            LOG.info("handleUpdateTag called on the server!")
            return
        }

        loadParts(tag)

        parts.values.forEach { part ->
            clientAddPart(part)
        }

        rebuildCollider()
    }

    //#endregion

    //#region Client Streaming

    // The following methods get called by our code (through forge), after we add new parts
    // Here, we send the freshly placed parts to clients that are already observing this multipart.

    @ServerOnly
    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        if (level!!.isClientSide) {
            return null
        }

        val tag = CompoundTag()

        packPlacementUpdates(tag)
        packPartUpdates(tag)

        return ClientboundBlockEntityDataPacket.create(this) { tag }
    }

    @ServerOnly
    private fun packPlacementUpdates(tag: CompoundTag) {
        if (placementUpdates.size == 0) {
            return
        }

        val placementUpdatesTag = ListTag()

        placementUpdates.forEach { update ->
            val part = update.part

            val updateTag = CompoundTag()

            updateTag.putPartUpdateType("Type", update.type)

            when (update.type) {
                PartUpdateType.Add -> {
                    updateTag.put("NewPart", savePartCommon(part))
                }

                PartUpdateType.Remove -> {
                    updateTag.putDirection("RemovedPartFace", part.placement.face)
                }
            }

            placementUpdatesTag.add(updateTag)
        }

        placementUpdates.clear()

        tag.put("PlacementUpdates", placementUpdatesTag)
    }

    @ServerOnly
    private fun packPartUpdates(tag: CompoundTag) {
        if (dirtyParts.size == 0) {
            return
        }

        val partUpdatesTag = ListTag()

        dirtyParts.forEach { face ->
            val part = parts[face]

            if (part == null) {
                LOG.error("Multipart at $pos part $face requested update, but was null")
                return@forEach
            }

            val syncTag = part.getSyncTag()

            if (syncTag == null) {
                //LOG.error("Part $part had an update enqueued, but returned a null sync tag")
                return@forEach
            }

            val updateTag = CompoundTag()
            updateTag.putDirection("Face", face)
            updateTag.put("SyncTag", syncTag)

            partUpdatesTag.add(updateTag)
        }

        dirtyParts.clear()

        tag.put("PartUpdates", partUpdatesTag)
    }

    @ClientOnly
    override fun onDataPacket(net: Connection?, packet: ClientboundBlockEntityDataPacket?) {
        if (packet == null) {
            LOG.error("onDataPacket null at $pos")
            return
        }

        if (level == null) {
            LOG.error("onDataPacket level null at $pos")
            return
        }

        if (!level!!.isClientSide) {
            LOG.error("onDataPacket called on the client!")
            return
        }

        val tag = packet.tag ?: return

        unpackPlacementUpdates(tag)
        unpackPartUpdates(tag)
    }

    @ClientOnly
    private fun unpackPlacementUpdates(tag: CompoundTag) {
        val placementUpdatesTag = tag.get("PlacementUpdates")
            as? ListTag
            ?: return

        placementUpdatesTag.map { it as CompoundTag }.forEach { updateTag ->
            when (updateTag.getPartUpdateType("Type")) {
                PartUpdateType.Add -> {
                    val newPartTag = updateTag.get("NewPart") as CompoundTag
                    val part = unpackPart(newPartTag)

                    if (parts.put(part.placement.face, part) != null) {
                        LOG.error("Client received new part, but a part was already present on the ${part.placement.face} face!")
                    }

                    clientAddPart(part)
                    joinCollider(part)
                }

                PartUpdateType.Remove -> {
                    val face = updateTag.getDirection("RemovedPartFace")
                    val part = destroyPart(face)

                    if (part == null) {
                        LOG.error("Client received broken part on $face, but there was no part present on the face!")
                    } else {
                        clientRemovePart(part)
                    }
                }
            }
        }
    }

    @ClientOnly
    private fun unpackPartUpdates(tag: CompoundTag) {
        val partUpdatesTag = tag.get("PartUpdates")
            as? ListTag
            ?: return

        partUpdatesTag.forEach { updateTag ->
            val compound = updateTag as CompoundTag

            val face = compound.getDirection("Face")

            val part = parts[face]

            if (part == null) {
                LOG.error("Multipart at $pos received update on $face, but part is null!")
            } else {
                val syncTag = compound.get("SyncTag") as CompoundTag
                part.handleSyncTag(syncTag)
            }
        }
    }

    /**
     * Enqueues a part for renderer setup.
     * */
    @ClientOnly
    private fun clientAddPart(part: Part<*>) {
        part.onAddedToClient()
        renderUpdates.add(PartUpdate(part, PartUpdateType.Add))
    }

    /**
     * Removes a part from the renderer.
     * */
    @ClientOnly
    private fun clientRemovePart(part: Part<*>) {
        part.onBroken()
        renderUpdates.add(PartUpdate(part, PartUpdateType.Remove))
    }

    //#endregion

    //#region Disk Loading

    @ServerOnly
    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        if (level!!.isClientSide) {
            return
        }

        try {
            saveParts(pTag, false)
        } catch (t: Throwable) {
            LOG.error("MULTIPART SAVE EX $t")
        }
    }

    /**
     * This method gets called when the tile entity is constructed.
     * The level is not available at this stage. We require this level to reconstruct the parts.
     * As such, we defer part reconstruction to a stage where the level becomes available (setLevel)
     * */
    @ServerOnly
    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        savedTag = pTag
    }

    /**
     * This method finishes loading from disk. It constructs all parts from the saved tag.
     * */
    @ServerOnly
    override fun setLevel(pLevel: Level) {
        try {
            super.setLevel(pLevel)

            if (pLevel.isClientSide) {
                return
            }

            if (this.savedTag != null) {
                loadParts(savedTag!!)

                parts.values.forEach { part ->
                    part.onLoaded()
                }

                // GC reference tracking
                savedTag = null
            } else {
                LOG.info("Multipart save tag null")
            }
        } catch (ex: Exception) {
            LOG.error("Unhandled exception in setLevel: $ex")
        }
    }

    //#endregion

    /**
     * This method notifies the parts that the chunk is being unloaded.
     * */
    override fun onChunkUnloaded() {
        super.onChunkUnloaded()

        parts.values.forEach { part ->
            part.onUnloaded()
        }
    }

    /**
     * Saves all the data associated with a part to a CompoundTag.
     * */
    @ServerOnly
    private fun savePartCommon(part: Part<*>): CompoundTag {
        val tag = CompoundTag()

        tag.putResourceLocation("ID", part.id)
        tag.putBlockPos("Pos", part.placement.position)
        tag.putDirection("Face", part.placement.face)
        tag.putDirection("Facing", part.placement.horizontalFacing)

        val customTag = part.getSaveTag()

        if (customTag != null) {
            tag.put("CustomTag", customTag)
        }

        return tag
    }

    /**
     * Saves the entire part set to a CompoundTag.
     * */
    @ServerOnly
    private fun saveParts(initial: Boolean): CompoundTag {
        val tag = CompoundTag()

        saveParts(tag, initial)

        return tag
    }

    /**
     * Saves the entire part set to the provided CompoundTag.
     * */
    @ServerOnly
    private fun saveParts(tag: CompoundTag, initial: Boolean) {
        assert(!level!!.isClientSide)

        val partsTag = ListTag()

        parts.keys.forEach { face ->
            val part = parts[face]
            val commonTag = savePartCommon(part!!)

            if (initial) {
                val initialTag = part.getInitialSyncTag()

                if (initialTag != null) {
                    commonTag.put("Initial", initialTag)
                }
            }

            partsTag.add(commonTag)
        }

        tag.put("Parts", partsTag)
    }

    /**
     * Loads all the parts from the tag and adds sets them up within this multipart.
     * This is used by the server to load from disk, and by the client to set up parts during the initial
     * chunk synchronization.
     * */
    private fun loadParts(tag: CompoundTag) {
        if (tag.contains("Parts")) {
            val partsTag = tag.get("Parts") as ListTag
            partsTag.forEach { partTag ->
                val partCompoundTag = partTag as CompoundTag
                val part = unpackPart(partCompoundTag)

                addPart(part.placement.face, part)

                val initialTag = partCompoundTag.get("Initial") as? CompoundTag

                if (initialTag != null) {
                    part.loadInitialSyncTag(initialTag)
                }
            }

            rebuildCollider()
        } else {
            LOG.error("Multipart at $pos had no saved data")
        }
    }

    /**
     * Creates a new part using the data provided in the tag.
     * This tag should be a product of the getPartTag method.
     * This method _does not_ add the part to the part map!
     * */
    private fun unpackPart(tag: CompoundTag): Part<*> {
        val id = tag.getResourceLocation("ID")
        val pos = tag.getBlockPos("Pos")
        val face = tag.getDirection("Face")
        val facing = tag.getDirection("Facing")
        val customTag = tag.get("CustomTag") as? CompoundTag

        val provider = PartRegistry.tryGetProvider(id) ?: error("Failed to get part with id $id")
        val part = provider.create(PartPlacementInfo(pos, face, facing, level!!, this, provider))

        if (customTag != null) {
            part.loadFromTag(customTag)
        }

        return part
    }

    /**
     * Gets a list of all cells within this container's parts.
     * */
    @ServerOnly
    override fun getCells(): ArrayList<Cell> {
        val results = ArrayList<Cell>()

        parts.values.forEach { part ->
            if (part is PartCellContainer<*>) {
                results.add(part.cell)
            }
        }

        return results
    }

    override fun neighborScan(actualCell: Cell): List<CellNeighborInfo> {
        val partFace = actualCell.locator.requireLocator<FaceLocator>()

        val part = parts[partFace]!!

        if (part !is PartCellContainer<*>) {
            error("FATAL! Queried neighbors for non-cell part!")
        }

        val results = LinkedHashSet<CellNeighborInfo>()

        val level = this.level ?: error("Level null in queryNeighbors")

        Base6Direction3dMask.perpendicular(partFace).process { searchDirection ->
            fun innerCellScan() {
                // Inner scan does not make sense outside multiparts, so I did not move it to CellScanner

                val innerFace = searchDirection.opposite

                val innerPart = parts[innerFace]
                    ?: return

                if (innerPart !is PartCellContainer<*>) {
                    return
                }

                if (!innerPart.allowInnerConnections) {
                    return
                }

                val innerCell = innerPart.cell

                if (!isConnectionAccepted(actualCell, innerCell)) {
                    return
                }

                results.add(
                    CellNeighborInfo(
                        innerCell,
                        this
                    )
                )
            }

            if (part.allowInnerConnections) {
                innerCellScan()
            }

            if (part.allowPlanarConnections) {
                planarCellScan(
                    level,
                    part.cell,
                    searchDirection,
                    results::add
                )
            }

            if (part.allowWrappedConnections) {
                wrappedCellScan(
                    level,
                    part.cell,
                    searchDirection,
                    results::add
                )
            }
        }

        part.addExtraConnections(results)

        return results.toList()
    }

    override fun onCellConnected(actualCell: Cell, remoteCell: Cell) {
        val innerFace = actualCell.locator.requireLocator<FaceLocator>()
        val part = parts[innerFace] as PartCellContainer<*>
        part.onConnected(remoteCell)
        part.onConnectivityChanged()
    }

    override fun onCellDisconnected(actualCell: Cell, remoteCell: Cell) {
        val part = parts[actualCell.locator.requireLocator<FaceLocator>()] as PartCellContainer<*>
        part.onDisconnected(remoteCell)
        if (part.hasCell && !part.cell.isBeingRemoved) {
            part.onConnectivityChanged()
        }
    }

    override fun onTopologyChanged() {
        setChanged()
    }

    @ServerOnly
    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(
            level as? ServerLevel ?: error("Tried to get multipart cell provider on the client")
        )

    fun use(player: Player, hand: InteractionHand): InteractionResult {
        val part = pickPart(player)
            ?: return InteractionResult.FAIL

        return part.onUsedBy(PartUseInfo(player, hand))
    }

    /**
     * I found that flywheel removes our instance sometimes, not sure why.
     * We use this to send the current parts to the renderer.
     * */
    fun bindRenderer(instance: MultipartBlockEntityInstance) {
        parts.values.forEach { part ->
            renderUpdates.add(PartUpdate(part, PartUpdateType.Add))
        }
    }

    fun unbindRenderer() {

    }

    fun addTicker(part: TickablePart): Boolean {
        if (level == null) {
            error("Illegal ticker add before level is available")
        }

        if (!parts.values.any { it == part }) {
            error("Cannot register ticker for a part that is not added!")
        }

        val result = tickingParts.add(part)

        if (!result) {
            return false
        }

        if (worldLoaded) {
            val chunk = level!!.getChunkAt(pos)
            chunk.updateBlockEntityTicker(this)
        }

        return true
    }

    fun hasTicker(part: TickablePart) = tickingParts.contains(part)

    fun markRemoveTicker(part: TickablePart) = tickingRemoveQueue.add(part)

    @ClientOnly
    fun hasAnimated(part: AnimatedPart): Boolean {
        requireIsOnRenderThread { "Tried to check if part $part has animate ticker on ${Thread.currentThread()}" }
        return animatedParts.contains(part)
    }

    @ClientOnly
    fun addAnimated(part: AnimatedPart): Boolean {
        requireIsOnRenderThread { "Tried to add part $part as animate on ${Thread.currentThread()}" }

        if (!parts.values.any { it == part }) {
            error("Cannot register animate for a part that is not added!")
        }

        return animatedParts.add(part)
    }

    fun markRemoveAnimated(part: AnimatedPart): Boolean {
        requireIsOnRenderThread { "Tried to remove part $part as animate on ${Thread.currentThread()}" }
        return animationRemoveQueue.add(part)
    }

    fun animateTick(randomSource: RandomSource) {
        for (part in animatedParts) {
            part.animationTick(randomSource)
        }

        for (removed in animationRemoveQueue) {
            animatedParts.remove(removed)
        }

        animationRemoveQueue.clear()
    }

    val needsTicks get() = tickingParts.isNotEmpty()

    companion object {
        fun <T : BlockEntity> serverTick(level: Level?, pos: BlockPos?, state: BlockState?, entity: T?) {
            if (entity !is MultipartBlockEntity) {
                LOG.error("Block tick entity is not a multipart!")
                return
            }

            if (level == null) {
                LOG.error("Block tick level was null")
                return
            }

            if (state == null) {
                LOG.error("Block tick BlockState was null")
                return
            }

            if (pos == null) {
                LOG.error("Block tick pos was null")
                return
            }

            entity.worldLoaded = true

            if (!entity.needsTicks) {
                // Remove the ticker

                val chunk = level.getChunkAt(pos)

                chunk.removeBlockEntityTicker(pos)

                return
            }

            entity.tickingParts.forEach { it.tick() }

            for (removed in entity.tickingRemoveQueue) {
                entity.tickingParts.remove(removed)
            }

            entity.tickingRemoveQueue.clear()
        }
    }

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        parts.values.forEach { part ->
            if (part is WailaNode) {
                part.appendWaila(builder, config)
            }
        }
    }
}
