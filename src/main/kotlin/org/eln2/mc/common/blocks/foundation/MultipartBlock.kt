package org.eln2.mc.common.blocks.foundation

import mcp.mobius.waila.api.IPluginConfig
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
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
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
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.DirectionMask
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet
import kotlin.collections.any
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.maxOf
import kotlin.collections.maxOfOrNull
import kotlin.collections.set

class MultipartBlock : BaseEntityBlock(Properties.of(Material.STONE)
    .noOcclusion()
    .destroyTime(0.2f)
    .lightLevel { it.getValue(GhostLightBlock.brightnessProperty) }) {

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
        return getMultipartShape(pLevel, pPos, pContext)
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

        if (level == null) {
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
            level.destroyBlock(pos, false)
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

    private fun getMultipartShape(level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val multipart = level.getBlockEntity(pos) as? MultipartBlockEntity ?: return emptyBox

        return multipart.collisionShape
    }

    private fun getPartShape(level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val pickedPart = pickPart(level, pos, context)
            ?: return emptyBox

        return pickedPart.shape
    }

    private fun pickPart(level: BlockGetter, pos: BlockPos, context: CollisionContext): Part<*>? {
        if (context !is EntityCollisionContext) {
            LOG.error("Collision context was not an entity collision context at $pos")
            return null
        }

        if (context.entity !is LivingEntity) {
            return null
        }

        return pickPart(level, pos, (context.entity as LivingEntity))
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
            MultipartBlockEntity.Companion::blockTick
        )
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(GhostLightBlock.brightnessProperty)
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

interface GhostLight {
    fun update(brightness: Int)
    fun destroy()
}

class GhostLightBlock : AirBlock(Properties.of(Material.AIR).lightLevel { it.getValue(brightnessProperty) }) {
    private class LightGrid(val level: Level) {
        private class Cell(val level: Level, val pos: BlockPos, val grid: LightGrid) {
            private fun handleBrightnessChanged(handle: Handle) {
                refreshGhost()
            }

            private fun handleDestroyed(handle: Handle) {
                handles.remove(handle)

                if (handles.size == 0) {
                    clearFromLevel(level, pos)
                    grid.onCellCleared(pos)
                }
            }

            private val handles = ArrayList<Handle>()

            fun createHandle(): GhostLight {
                return Handle(this).also { handles.add(it) }
            }

            fun refreshGhost() {
                LOG.info("Refresh ghost")

                val maximalBrightness = handles.maxOf { it.trackedBrightness }

                setInLevel(level, pos, maximalBrightness)
            }

            private class Handle(val cell: Cell) : GhostLight {
                var trackedBrightness: Int = 0

                var destroyed = false

                override fun update(brightness: Int) {
                    if (destroyed) {
                        error("Cannot set brightness, handle destroyed!")
                    }

                    if (brightness == trackedBrightness) {
                        return
                    }

                    trackedBrightness = brightness

                    cell.handleBrightnessChanged(this)
                }

                override fun destroy() {
                    if (!destroyed) {
                        cell.handleDestroyed(this)
                    }
                }
            }
        }

        private val cells = HashMap<BlockPos, Cell>()

        fun onCellCleared(pos: BlockPos) {
            cells.remove(pos)
        }

        fun createHandle(pos: BlockPos): GhostLight {
            return cells.computeIfAbsent(pos) { Cell(level, pos, this) }.createHandle()
        }

        fun refreshGhost(pos: BlockPos) {
            cells[pos]?.refreshGhost()
        }
    }

    companion object {
        private val block get() = BlockRegistry.LIGHT_GHOST_BLOCK.block.get()

        val brightnessProperty: IntegerProperty = IntegerProperty.create("brightness", 0, 15)

        private val grids = HashMap<Level, LightGrid>()

        private fun setInLevel(level: Level, pos: BlockPos, brightness: Int): Boolean {
            val previousBlockState = level.getBlockState(pos)

            if (previousBlockState.block != Blocks.AIR && previousBlockState.block != block) {
                LOG.info("Could not place, existing block there: $previousBlockState")
                return false
            }

            if (previousBlockState.block != block || previousBlockState.getValue(brightnessProperty) != brightness) {
                level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(brightnessProperty, brightness))
                LOG.info("Placed")
                return true
            }

            return false
        }

        private fun clearFromLevel(level: Level, pos: BlockPos): Boolean {
            val state = level.getBlockState(pos)

            if (state.block != block) {
                LOG.error("Cannot remove: not ghost light")

                return false
            }

            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())

            return true
        }

        private fun getGrid(level: Level): LightGrid {
            return grids.computeIfAbsent(level) { LightGrid(level) }
        }

        fun createHandle(level: Level, pos: BlockPos): GhostLight {
            return getGrid(level).createHandle(pos)
        }

        fun refreshGhost(level: Level, pos: BlockPos) {
            grids[level]?.refreshGhost(pos)
        }
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(brightnessProperty)
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
    WailaEntity,
    DataEntity {

    // Interesting issue.
    // If we try to add tickers before the block receives the first tick,
    // we will cause some deadlock in Minecraft's code.
    // This is set to TRUE when this block first ticks. We only update the ticker if this is set to true.
    private var worldLoaded = false

    private val parts = HashMap<Direction, Part<*>>()

    // Used for part sync:
    private val syncingParts = ArrayList<Direction>()
    private val placementUpdates = ArrayList<PartUpdate>()

    // Used for disk loading:
    private var savedTag: CompoundTag? = null

    private var tickingParts = ArrayList<TickablePart>()

    // This is useful, because a part might remove its ticker whilst being ticked
    // (which would cause our issues with iteration)
    private var tickingRemoveQueue = ArrayDeque<TickablePart>()

    val isEmpty = parts.isEmpty()

    // Used for rendering:
    @ClientOnly
    val renderQueue = ConcurrentLinkedQueue<PartUpdate>()

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

        dataNode.children.removeIf { it == result.dataNode }

        tickingParts.removeIf { it == result }

        result.onRemoved()

        level?.also {
            if (!it.isClientSide) {
                // Remove lingering lights

                updateBrightness()
            }
        }

        return result
    }

    private fun addPart(face: Direction, part: Part<*>) {
        parts[face] = part
        dataNode.withChild(part.dataNode)
        part.onAdded()
    }

    /**
     * Ensures that this multipart will be saved at a later date.
     * */
    @ServerOnly
    private fun saveData() {
        setChanged()
    }

    /**
     * Enqueues this multipart for synchronization to clients.
     * */
    @ServerOnly
    private fun syncData() {
        level!!.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /**
     * Enqueues a part for synchronization to clients.
     * This is used to synchronize custom part data.
     * */
    @ServerOnly
    fun enqueuePartSync(face: Direction) {
        syncingParts.add(face)
        syncData() // TODO: Can we batch multiple updates?
    }

    /**
     * Finds the part intersected by the entity's view.
     * */
    fun pickPart(entity: LivingEntity): Part<*>? {
        return clipScene(entity, { it.gridBoundingBox }, parts.values)
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
    ): Boolean {
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

        val placeDirection = if (face.isVertical()) {
            entity.direction
        } else {
            Direction.NORTH
        }

        val placementContext = PartPlacementInfo(pos, face, placeDirection, level, this)

        val worldBoundingBox = PartGeometry.worldBoundingBox(
            provider.placementCollisionSize,
            placementContext.horizontalFacing,
            placementContext.face,
            placementContext.pos
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

        if (part is ItemPersistentPart && part.order == PersistentPartLoadOrder.BeforeSim) {
            part.loadItemTag(saveTag)
        }

        part.onPlaced()

        if (part is PartCellContainer) {
            CellConnections.insertFresh(this, part.cell)
        }

        if (part is ItemPersistentPart && part.order == PersistentPartLoadOrder.AfterSim) {
            part.loadItemTag(saveTag)
        }

        if (part is PartCellContainer) {
            part.cell.bindGameObjects(listOf(this, part))
        }

        saveData()
        syncData()

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
        if (part is PartCellContainer) {
            part.cell.unbindGameObjects()

            CellConnections.destroy(
                part.cell,
                this
            )
        }

        if (part is ItemPersistentPart && saveTag != null) {
            part.saveItemTag(saveTag)
        }

        destroyPart(part.placement.face)
        placementUpdates.add(PartUpdate(part, PartUpdateType.Remove))

        part.onBroken()

        saveData()
        syncData()
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
        collisionShape = Shapes.join(collisionShape, part.shape, BooleanOp.OR)
    }

    /**
     * Builds the collider from the current parts.
     * */
    private fun rebuildCollider() {
        collisionShape = Shapes.empty()

        parts.values.map { it.shape }.forEach {
            collisionShape = Shapes.joinUnoptimized(collisionShape, it, BooleanOp.OR)
        }

        collisionShape.optimize()
    }

    //#region Client Chunk Synchronization

    // The following methods get called when chunks are first synchronized to clients
    // Here, we send all the parts we have.

    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        if (level!!.isClientSide) {
            return CompoundTag()
        }

        return saveParts()
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
                    updateTag.put("NewPart", savePart(part))
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
        if (syncingParts.size == 0) {
            return
        }

        val partUpdatesTag = ListTag()

        syncingParts.forEach { face ->
            val part = parts[face]

            if (part == null) {
                LOG.error("Multipart at $pos part $face requested update, but was null")
                return@forEach
            }

            val syncTag = part.getSyncTag()

            if (syncTag == null) {
                LOG.error("Part $part had an update enqueued, but returned a null sync tag")
                return@forEach
            }

            val updateTag = CompoundTag()
            updateTag.putDirection("Face", face)
            updateTag.put("SyncTag", syncTag)

            partUpdatesTag.add(updateTag)
        }

        syncingParts.clear()

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
        renderQueue.add(PartUpdate(part, PartUpdateType.Add))
    }

    /**
     * Removes a part from the renderer.
     * */
    @ClientOnly
    private fun clientRemovePart(part: Part<*>) {
        part.onBroken()
        renderQueue.add(PartUpdate(part, PartUpdateType.Remove))
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
            saveParts(pTag)
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
    private fun savePart(part: Part<*>): CompoundTag {
        val tag = CompoundTag()

        tag.putResourceLocation("ID", part.id)
        tag.putBlockPos("Pos", part.placement.pos)
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
    private fun saveParts(): CompoundTag {
        val tag = CompoundTag()

        saveParts(tag)

        return tag
    }

    /**
     * Saves the entire part set to the provided CompoundTag.
     * */
    @ServerOnly
    private fun saveParts(tag: CompoundTag) {
        assert(!level!!.isClientSide)

        val partsTag = ListTag()

        parts.keys.forEach { face ->
            val part = parts[face]

            partsTag.add(savePart(part!!))
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
                val part = unpackPart(partTag as CompoundTag)

                addPart(part.placement.face, part)
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
        val part = provider.create(PartPlacementInfo(pos, face, facing, level!!, this))

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
            if (part is PartCellContainer) {
                results.add(part.cell)
            }
        }

        return results
    }

    override fun neighborScan(actualCell: Cell): ArrayList<CellNeighborInfo> {
        val partFace = actualCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator>().faceWorld

        val part = parts[partFace]!!

        if (part !is PartCellContainer) {
            error("FATAL! Queried neighbors for non-cell part!")
        }

        val results = LinkedHashSet<CellNeighborInfo>()

        val level = this.level ?: error("Level null in queryNeighbors")

        DirectionMask.perpendicular(partFace).process { searchDirection ->
            fun innerScan() {
                // Inner scan does not make sense outside multiparts, so I did not move it to CellScanner

                if (part.allowInnerConnections) {
                    val innerFace = searchDirection.opposite

                    val innerPart = parts[innerFace]
                        ?: return

                    if (innerPart !is PartCellContainer) {
                        return
                    }

                    if (!innerPart.allowInnerConnections) {
                        return
                    }

                    if (!innerPart.cell.acceptsConnection(innerPart.cell)) {
                        return
                    }

                    results.add(
                        CellNeighborInfo(
                            innerPart.cell,
                            this
                        )
                    )
                }
            }

            innerScan()

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

        return ArrayList(results)
    }

    override fun onCellConnected(actualCell: Cell, remoteCell: Cell) {
        val innerFace = actualCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator>().faceWorld
        val part = parts[innerFace] as PartCellContainer
        part.onConnected(remoteCell)
    }

    override fun onCellDisconnected(actualCell: Cell, remoteCell: Cell) {
        val part = parts[actualCell.posDescr.requireLocator<SO3, BlockFaceLocator>().faceWorld] as PartCellContainer
        part.onDisconnected(remoteCell)
    }

    override fun onTopologyChanged() {
        saveData()
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
            renderQueue.add(PartUpdate(part, PartUpdateType.Add))
        }
    }

    fun unbindRenderer() {

    }

    fun addTicker(part: TickablePart) {
        if (level == null) {
            error("Illegal ticker add before level is available")
        }

        if (!parts.values.any { it == part }) {
            error("Cannot register ticker for a part that is not added!")
        }

        if (tickingParts.contains(part)) {
            error("Duplicate add ticking part $part")
        }

        tickingParts.add(part)

        if (!worldLoaded) {
            return
        }

        val chunk = level!!.getChunkAt(pos)

        chunk.updateBlockEntityTicker(this)
    }

    fun removeTicker(part: TickablePart) {
        tickingRemoveQueue.add(part)
    }

    val needsTicks get() = tickingParts.isNotEmpty()

    private fun setBlockBrightness(value: Int) {
        level!!.setBlockAndUpdate(pos, blockState.setValue(GhostLightBlock.brightnessProperty, value))
    }

    fun updateBrightness() {
        if (level!!.isClientSide) {
            error("Cannot update brightness on client")
        }

        if (!worldLoaded) {
            return
        }

        val currentBrightness = blockState.getValue(GhostLightBlock.brightnessProperty)

        val targetBrightness = parts.values.maxOfOrNull { it.brightness }

        if (targetBrightness == null) {
            setBlockBrightness(0)
            return
        }

        if (targetBrightness != currentBrightness) {
            setBlockBrightness(targetBrightness)
        }
    }

    companion object {
        fun <T : BlockEntity> blockTick(level: Level?, pos: BlockPos?, state: BlockState?, entity: T?) {
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

            if (!level.isClientSide) {
                entity.updateBrightness()
            }

            if (!entity.needsTicks) {
                // Remove the ticker

                val chunk = level.getChunkAt(pos)

                chunk.removeBlockEntityTicker(pos)

                return
            }

            entity.tickingParts.forEach { it.tick() }

            while (entity.tickingRemoveQueue.isNotEmpty()) {
                val removed = entity.tickingRemoveQueue.removeFirst()

                if (!entity.tickingParts.remove(removed)) {
                    error("Tried to remove part ticker $removed that was not registered")
                }
            }
        }
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        parts.values.forEach { part ->
            if (part !is WailaEntity) {
                return@forEach
            }

            part.appendBody(builder, config)
        }
    }

    override val dataNode: DataNode = DataNode()
}
