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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.annotations.ClientOnly
import org.eln2.mc.annotations.ServerOnly
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.content.GhostLightBlock
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.BlockPosExtensions.directionTo
import org.eln2.mc.extensions.BlockPosExtensions.minus
import org.eln2.mc.extensions.BlockPosExtensions.plus
import org.eln2.mc.extensions.DirectionExtensions.isVertical
import org.eln2.mc.extensions.NbtExtensions.getBlockPos
import org.eln2.mc.extensions.NbtExtensions.getDirection
import org.eln2.mc.extensions.NbtExtensions.getPartUpdateType
import org.eln2.mc.extensions.NbtExtensions.getResourceLocation
import org.eln2.mc.extensions.NbtExtensions.putBlockPos
import org.eln2.mc.extensions.NbtExtensions.putDirection
import org.eln2.mc.extensions.NbtExtensions.putPartUpdateType
import org.eln2.mc.extensions.NbtExtensions.putResourceLocation
import org.eln2.mc.extensions.PartExtensions.allows
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.utility.BoundingBox
import java.util.concurrent.ConcurrentLinkedQueue

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
    ICellContainer,
    IWailaProvider {

    // Interesting issue.
    // If we try to add tickers before the block receives the first tick,
    // we will cause some deadlock in Minecraft's code.
    // This is set to TRUE when this block first ticks. We only update the ticker if this is set to true.
    private var worldLoaded = false

    private val parts = HashMap<Direction, Part>()

    // Used for part sync:
    private val syncingParts = ArrayList<Direction>()
    private val placementUpdates = ArrayList<PartUpdate>()

    // Used for disk loading:
    private var savedTag: CompoundTag? = null

    private var tickingParts = ArrayList<ITickablePart>()

    // This is useful, because a part might remove its ticker whilst being ticked
    // (which would cause our issues with iteration)
    private var tickingRemoveQueue = ArrayDeque<ITickablePart>()

    val isEmpty = parts.isEmpty()

    // Used for rendering:
    @ClientOnly
    val renderQueue = ConcurrentLinkedQueue<PartUpdate>()

    var collisionShape: VoxelShape private set

    init {
        collisionShape = Shapes.empty()
    }

    fun getPart(face: Direction): Part? {
        return parts[face]
    }

    private fun destroyPart(face: Direction): Part? {
        val result = parts.remove(face)
            ?: return null

        tickingParts.removeIf { it == result }

        result.onRemoved()

        level?.also {
            if(!it.isClientSide){
                // Remove lingering lights

                updateBrightness()
            }
        }

        return result
    }

    private fun addPart(face: Direction, part: Part) {
        parts[face] = part
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
    fun pickPart(entity: LivingEntity): Part? {
        return BoundingBox.clipScene(entity, { it.gridBoundingBox }, parts.values)
    }

    /**
     * Attempts to place a part.
     * @return True if the part was successfully placed. Otherwise, false.
     * */
    @ServerOnly
    fun place(entity: Player, pos: BlockPos, face: Direction, provider: PartProvider, saveTag: CompoundTag? = null): Boolean {
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
            Eln2.LOGGER.info("Cannot place on non-full block")
            return false
        }

        val placeDirection = if (face.isVertical()) {
            entity.direction
        } else {
            Direction.NORTH
        }

        val placementContext = PartPlacementContext(pos, face, placeDirection, level, this)

        val worldBoundingBox = PartTransformations.worldBoundingBox(
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

        val part = provider.create(placementContext)

        addPart(face, part)

        placementUpdates.add(PartUpdate(part, PartUpdateType.Add))
        joinCollider(part)

        if(part is IItemPersistentPart && part.order == ItemPersistentPartLoadOrder.BeforeSim) {
            part.loadItemTag(saveTag)
        }

        part.onPlaced()

        if (part is IPartCellContainer) {
            CellConnectionManager.connect(this, CellInfo(part.cell, part.placementContext.face))
        }

        if(part is IItemPersistentPart && part.order == ItemPersistentPartLoadOrder.AfterSim) {
            part.loadItemTag(saveTag)
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
    fun breakPart(part: Part, saveTag: CompoundTag? = null) {
        if (part is IPartCellContainer) {
            CellConnectionManager.destroy(
                CellInfo(
                    part.cell,
                    part.placementContext.face
                ),
                this
            )
        }

        if(part is IItemPersistentPart && saveTag != null) {
            part.saveItemTag(saveTag)
        }

        destroyPart(part.placementContext.face)
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

        val direction = pos.directionTo(neighborPos)

        if (direction == null) {
            Eln2.LOGGER.error("Failed to get direction")
            return false
        } else {
            Eln2.LOGGER.info("Face: $direction")
        }

        if (parts.containsKey(direction)) {
            breakPart(parts[direction]!!)
        }

        return parts.size == 0
    }

    /**
     * Merges the current multipart collider with the collider of the part.
     * */
    private fun joinCollider(part: Part) {
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
            Eln2.LOGGER.error("Part update tag was null at $pos")
            return
        }

        if (level == null) {
            Eln2.LOGGER.error("Level was null in handleUpdateTag at $pos")
            return
        }

        if (!level!!.isClientSide) {
            Eln2.LOGGER.info("handleUpdateTag called on the server!")
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
                    updateTag.putDirection("RemovedPartFace", part.placementContext.face)
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
                Eln2.LOGGER.error("Multipart at $pos part $face requested update, but was null")
                return@forEach
            }

            val syncTag = part.getSyncTag()

            if (syncTag == null) {
                Eln2.LOGGER.error("Part $part had an update enqueued, but returned a null sync tag")
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
            Eln2.LOGGER.error("onDataPacket null at $pos")
            return
        }

        if (level == null) {
            Eln2.LOGGER.error("onDataPacket level null at $pos")
            return
        }

        if (!level!!.isClientSide) {
            Eln2.LOGGER.error("onDataPacket called on the client!")
            return
        }

        val tag = packet.tag

        if(tag == null){
            Eln2.LOGGER.error("Got null update tag")
            return
        }

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

                    if (parts.put(part.placementContext.face, part) != null) {
                        Eln2.LOGGER.error("Client received new part, but a part was already present on the ${part.placementContext.face} face!")
                    }

                    clientAddPart(part)
                    joinCollider(part)
                }

                PartUpdateType.Remove -> {
                    val face = updateTag.getDirection("RemovedPartFace")
                    val part = destroyPart(face)

                    if (part == null) {
                        Eln2.LOGGER.error("Client received broken part on $face, but there was no part present on the face!")
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
                Eln2.LOGGER.error("Multipart at $pos received update on $face, but part is null!")
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
    private fun clientAddPart(part: Part) {
        part.onAddedToClient()
        renderQueue.add(PartUpdate(part, PartUpdateType.Add))
    }

    /**
     * Removes a part from the renderer.
     * */
    @ClientOnly
    private fun clientRemovePart(part: Part) {
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
        }
        catch (t: Throwable){
            Eln2.LOGGER.error("MULTIPART SAVE EX $t")
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
                Eln2.LOGGER.info("Multipart save tag null")
            }
        } catch (ex: Exception) {
            Eln2.LOGGER.error("Unhandled exception in setLevel: $ex")
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
    private fun savePart(part: Part): CompoundTag {
        val tag = CompoundTag()

        tag.putResourceLocation("ID", part.id)
        tag.putBlockPos("Pos", part.placementContext.pos)
        tag.putDirection("Face", part.placementContext.face)
        tag.putDirection("Facing", part.placementContext.horizontalFacing)

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

                addPart(part.placementContext.face, part)
            }

            rebuildCollider()
        } else {
            Eln2.LOGGER.error("Multipart at $pos had no saved data")
        }
    }

    /**
     * Creates a new part using the data provided in the tag.
     * This tag should be a product of the getPartTag method.
     * This method _does not_ add the part to the part map!
     * */
    private fun unpackPart(tag: CompoundTag): Part {
        val id = tag.getResourceLocation("ID")
        val pos = tag.getBlockPos("Pos")
        val face = tag.getDirection("Face")
        val facing = tag.getDirection("Facing")
        val customTag = tag.get("CustomTag") as? CompoundTag

        val provider = PartRegistry.tryGetProvider(id) ?: error("Failed to get part with id $id")
        val part = provider.create(PartPlacementContext(pos, face, facing, level!!, this))

        if (customTag != null) {
            part.loadFromTag(customTag)
        }

        return part
    }

    /**
     * Gets a list of all cells within this container's parts.
     * */
    @ServerOnly
    override fun getCells(): ArrayList<CellInfo> {
        val results = ArrayList<CellInfo>()

        parts.values.forEach { part ->
            if (part is IPartCellContainer) {
                results.add(CellInfo(part.cell, part.placementContext.face))
            }
        }

        return results
    }

    override fun query(query: CellQuery): CellInfo? {
        val part = parts[query.surface]

        if (part == null) {
            Eln2.LOGGER.info("No part on face ${query.surface}")
            return null
        }

        val cellContainer = part as? IPartCellContainer

        if (cellContainer == null) {
            Eln2.LOGGER.info("Part on face ${query.surface} is not a cell container")
            return null
        }

        val relativeRotation = part.getRelativeDirection(query.connectionFace)

        Eln2.LOGGER.info("${query.connectionFace} mapped to $relativeRotation")

        if (cellContainer.provider.canConnectFrom(relativeRotation)) {
            Eln2.LOGGER.info("Connection accepted!")
            return CellInfo(cellContainer.cell, query.surface)
        }

        Eln2.LOGGER.info("Connection rejected")

        return null
    }

    override fun queryNeighbors(location: CellInfo): ArrayList<CellNeighborInfo> {
        val partFace = location.innerFace

        val part = parts[partFace]!!

        if (part !is IPartCellContainer) {
            error("FATAL! Queried neighbors for non-cell part!")
        }

        val results = LinkedHashSet<CellNeighborInfo>()

        DirectionMask.perpendicular(partFace).process { searchDirection ->
            val partRelative = part.getRelativeDirection(searchDirection)

            fun scanConsumer(remoteSpace: CellInfo, remoteContainer: ICellContainer, remoteRelative: RelativeRotationDirection){
                results.add(CellNeighborInfo(remoteSpace, remoteContainer, partRelative, remoteRelative))
            }

            if (!part.provider.canConnectFrom(partRelative)) {
                Eln2.LOGGER.info("Part rejected connection on $partRelative - ${part.provider}")
                return@process
            }

            fun innerScan() {
                // Inner scan does not make sense outside multiparts, so I did not move it to CellScanner

                if (part.allowInnerConnections) {
                    val innerFace = searchDirection.opposite

                    val innerPart = parts[innerFace]
                        ?: return

                    if (innerPart !is IPartCellContainer) {
                        return
                    }

                    if (!innerPart.allowInnerConnections) {
                        return
                    }

                    val innerRelativeRotation = innerPart.getRelativeDirection(partFace.opposite)

                    if (!innerPart.provider.canConnectFrom(innerRelativeRotation)) {
                        return
                    }

                    results.add(
                        CellNeighborInfo(
                            CellInfo(innerPart.cell, innerPart.placementContext.face),
                            this,
                            partRelative,
                            innerRelativeRotation
                        )
                    )
                }
            }

            fun planarScan() {
                if (part.allowPlanarConnections) {
                    CellScanner.planarScan(level!!, pos, searchDirection, partFace, ::scanConsumer)
                }
            }

            fun wrappedScan() {
                if (part.allowWrappedConnections) {
                    CellScanner.wrappedScan(level!!, pos, searchDirection, partFace, ::scanConsumer)
                }
            }

            innerScan()
            planarScan()
            wrappedScan()
        }

        return ArrayList(results)
    }

    override fun probeConnectionCandidate(location: CellInfo, direction: Direction, mode: ConnectionMode): RelativeRotationDirection? {
        val part = (parts[location.innerFace]!!)
        val partCell = part as IPartCellContainer

        if(!partCell.allows(mode)){
            return null
        }

        val relative = part.getRelativeDirection(direction)

        return if (partCell.provider.canConnectFrom(relative)) {
            relative
        } else {
            null
        }
    }

    override fun recordConnection(location: CellInfo, direction: RelativeRotationDirection, neighborSpace: CellInfo) {
        val part = parts[location.innerFace] as IPartCellContainer

        val cellPos = location.cell.pos
        val neighborPos = neighborSpace.cell.pos

        val mode: ConnectionMode = if (cellPos.blockPos == neighborPos.blockPos) {
            // They are in the same block space. This is certainly an inner connection.

            ConnectionMode.Inner
        } else if (location.innerFace == neighborSpace.innerFace) {
            // This is planar. If it were wrapped, the normals would be perpendicular (i.e. not equal)

            ConnectionMode.Planar
        } else {
            ConnectionMode.Wrapped
        }

        part.recordConnection(direction, mode)
    }

    override fun recordDeletedConnection(location: CellInfo, direction: RelativeRotationDirection) {
        val part = parts[location.innerFace] as IPartCellContainer

        part.recordDeletedConnection(direction)
    }

    override fun topologyChanged() {
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

        return part.onUsedBy(PartUseContext(player, hand))
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

    fun addTicker(part: ITickablePart) {
        if(level == null){
            error("Illegal ticker add before level is available")
        }

        if (!parts.values.any { it == part }) {
            error("Cannot register ticker for a part that is not added!")
        }

        if (tickingParts.contains(part)) {
            error("Duplicate add ticking part $part")
        }

        tickingParts.add(part)

        if(!worldLoaded){
            return
        }

        val chunk = level!!.getChunkAt(pos)

        chunk.updateBlockEntityTicker(this)
    }

    fun removeTicker(part: ITickablePart) {
        tickingRemoveQueue.add(part)
    }

    val needsTicks get() = tickingParts.isNotEmpty()

    private fun setBlockBrightness(value: Int){
        level!!.setBlockAndUpdate(pos, blockState.setValue(GhostLightBlock.brightnessProperty, value))
    }

    fun updateBrightness() {
        if(level!!.isClientSide){
            error("Cannot update brightness on client")
        }

        if(!worldLoaded) {
            return
        }

        val currentBrightness = blockState.getValue(GhostLightBlock.brightnessProperty)

        val targetBrightness = parts.values.maxOfOrNull { it.brightness }

        if(targetBrightness == null) {
            setBlockBrightness(0)
            return
        }

        if(targetBrightness != currentBrightness) {
            setBlockBrightness(targetBrightness)
        }
    }

    companion object {
        fun <T : BlockEntity> blockTick(level: Level?, pos: BlockPos?, state: BlockState?, entity: T?) {
            if (entity !is MultipartBlockEntity) {
                Eln2.LOGGER.error("Block tick entity is not a multipart!")
                return
            }

            if (level == null) {
                Eln2.LOGGER.error("Block tick level was null")
                return
            }

            if (state == null) {
                Eln2.LOGGER.error("Block tick BlockState was null")
                return
            }

            if (pos == null) {
                Eln2.LOGGER.error("Block tick pos was null")
                return
            }

            entity.worldLoaded = true

            if(!level.isClientSide) {
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

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        parts.values.forEach { part ->
            if (part !is IWailaProvider) {
                return@forEach
            }

            part.appendBody(builder, config)
        }
    }
}
