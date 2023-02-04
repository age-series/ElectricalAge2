package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
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
import org.eln2.mc.common.DirectionMask
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellConnectionManager
import org.eln2.mc.common.cell.CellGraphManager
import org.eln2.mc.common.cell.container.CellNeighborInfo
import org.eln2.mc.common.cell.container.CellSpaceLocation
import org.eln2.mc.common.cell.container.CellSpaceQuery
import org.eln2.mc.common.cell.container.ICellContainer
import org.eln2.mc.common.parts.*
import org.eln2.mc.common.parts.part.WirePart
import org.eln2.mc.extensions.BlockPosExtensions.directionTo
import org.eln2.mc.extensions.BlockPosExtensions.minus
import org.eln2.mc.extensions.BlockPosExtensions.plus
import org.eln2.mc.extensions.DirectionExtensions.isVertical
import org.eln2.mc.extensions.NbtExtensions.getBlockPos
import org.eln2.mc.extensions.NbtExtensions.getDirection
import org.eln2.mc.extensions.NbtExtensions.getResourceLocation
import org.eln2.mc.extensions.NbtExtensions.putBlockPos
import org.eln2.mc.extensions.NbtExtensions.setDirection
import org.eln2.mc.extensions.NbtExtensions.setResourceLocation
import org.eln2.mc.utility.AABBUtilities
import org.eln2.mc.utility.ClientOnly
import org.eln2.mc.utility.ServerOnly
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Multipart entities
 *  - Are dummy entities, that do not have any special data or logic by themselves
 *  - Act as a container for Parts. There may be one part per inner face (maximum of 6 parts per multipart entity)
 *  - The player can place inside the multipart entity. Placement and breaking logic must be emulated.
 *  - The multipart entity saves data for all the parts. Parts are responsible for their rendering.
 *  - A part can also be included in the simulation. Special connection logic is implemented for CellPart.
 * */
class MultipartBlockEntity (var pos : BlockPos, var state: BlockState) :
    BlockEntity(BlockRegistry.MULTIPART_BLOCK_ENTITY.get(), pos, state),
    ICellContainer {

    private val parts = HashMap<Direction, Part>()

    // Used for part sync:
    private val partsRequestingUpdate = ArrayList<Direction>()

    // Used for streaming to clients:
    private val changedParts = ArrayList<PartUpdate>()

    // Used for disk loading:
    private var savedTag : CompoundTag? = null

    // Used for rendering:
    @ClientOnly
    val clientUpdateQueue = ConcurrentLinkedQueue<PartUpdate>()

    var collisionShape : VoxelShape
        private set

    init {
        collisionShape = Shapes.empty()
    }

    /**
     * Ensures that this multipart will be saved at a later date.
     * */
    @ServerOnly
    private fun saveData(){
        setChanged()
    }

    /**
     * Enqueues this multipart for synchronization to clients.
     * */
    @ServerOnly
    private fun syncData(){
        level!!.sendBlockUpdated(blockPos, state, state, Block.UPDATE_CLIENTS)
    }

    /**
     * Enqueues a part for synchronization to clients.
     * This is used to synchronize custom part data.
     * */
    @ServerOnly
    fun enqueuePartSync(face : Direction){
        partsRequestingUpdate.add(face)
        syncData() // TODO: Can we batch multiple updates?
    }

    /**
     * Finds the part intersected by the entity's view.
     * */
    fun pickPart(entity : LivingEntity) : Part?{
        return AABBUtilities.clipScene(entity, { it.gridBoundingBox }, parts.values)
    }

    /**
     * Attempts to place a part.
     * @return True if the part was successfully placed. Otherwise, false.
     * */
    @ServerOnly
    fun place(entity: Player, pos : BlockPos, face : Direction, provider : PartProvider) : Boolean{
        if(entity.level.isClientSide){
            return false
        }

        val level = entity.level as ServerLevel

        if(parts.containsKey(face)){
            return false
        }

        val neighborPos = pos - face
        val targetBlockState = level.getBlockState(neighborPos)

        if(!targetBlockState.isCollisionShapeFullBlock(level, neighborPos)){
            Eln2.LOGGER.info("Cannot place on non-full block")
            return false
        }

        val placeDirection = if(face.isVertical()){
            entity.direction
        }
        else{
            Direction.NORTH
        }

        val part = provider.create(PartPlacementContext(pos, face, placeDirection, level, this))

        parts[face] = part

        changedParts.add(PartUpdate(part, PartUpdateType.Add))
        joinCollider(part)

        part.onPlaced()

        if(part is IPartCellContainer){
            CellConnectionManager.connect(this, CellSpaceLocation(part.cell, part.placementContext.face))
        }

        saveData()
        syncData()

        return true
    }

    /**
     * Tries to destroy a part.
     * @return True if there are no parts left after breaking and, as such, this entity should be destroyed. Otherwise, false.
     * */
    // TODO: return a break result (so we can drop the item)
    @ServerOnly
    fun remove(entity : Player, level : Level, pos : BlockPos) : Boolean{
        if(level.isClientSide){
            return false
        }

        val part = pickPart(entity) ?: return false

        removePart(part)

        return parts.size == 0
    }

    /**
     * Destroys a part, saves and synchronizes the changes.
     * */
    @ServerOnly
    private fun removePart(part : Part){
        if(part is IPartCellContainer){
            CellConnectionManager.destroy(CellSpaceLocation(
                part.cell,
                part.placementContext.face),
                this
            )
        }

        parts.remove(part.placementContext.face)
        changedParts.add(PartUpdate(part, PartUpdateType.Remove))

        part.onDestroyed()

        saveData()
        syncData()
    }

    /**
     * Called by the block when a neighbor is destroyed.
     * If a part is placed on the face corresponding to that neighbor,
     * the part must be destroyed.
     * */
    @ServerOnly
    fun onNeighborDestroyed(neighborPos : BlockPos) : Boolean{
        if(level!!.isClientSide){
            return false
        }

        val direction = pos.directionTo(neighborPos)

        if(direction == null){
            Eln2.LOGGER.error("Failed to get direction")
            return false
        }
        else{
            Eln2.LOGGER.info("Face: $direction")
        }

        if(parts.containsKey(direction)){
            removePart(parts[direction]!!)
        }

        return parts.size == 0
    }

    /**
     * Merges the current multipart collider with the collider of the part.
     * */
    private fun joinCollider(part : Part){
        collisionShape = Shapes.join(collisionShape, part.shape, BooleanOp.OR)
    }

    /**
     * Builds the collider from the current parts.
     * */
    private fun rebuildCollider(){
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
        if(level!!.isClientSide){
            return CompoundTag()
        }

        return savePartsToTag()
    }

    @ClientOnly
    override fun handleUpdateTag(tag: CompoundTag?) {
        if(tag == null){
            Eln2.LOGGER.error("Part update tag was null at $pos")
            return
        }

        if(level == null){
            Eln2.LOGGER.error("Level was null in handleUpdateTag at $pos")
            return
        }

        if(!level!!.isClientSide){
            Eln2.LOGGER.info("handleUpdateTag called on the server!")
            return
        }

        loadPartsFromTag(tag)

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
        if(level!!.isClientSide){
            return null
        }

        val tag = CompoundTag()

        // todo: remove allocations and unify
        val newPartsTag = ListTag()
        val removedPartsTag = ListTag()

        changedParts.forEach { update ->
            val part = update.part

            when(update.type){
                PartUpdateType.Add -> {
                    newPartsTag.add(savePartToTag(part))
                }
                PartUpdateType.Remove -> {
                    val faceTag = CompoundTag()
                    faceTag.setDirection("Face", part.placementContext.face)
                    removedPartsTag.add(faceTag)
                }
            }
        }

        changedParts.clear()

        if(newPartsTag.size > 0){
            tag.put("NewParts", newPartsTag)
        }

        if(removedPartsTag.size > 0){
            tag.put("RemovedParts", removedPartsTag)
        }

        if(partsRequestingUpdate.size > 0){
            val partUpdatesTag = ListTag()

            partsRequestingUpdate.forEach{ face ->
                val part = parts[face]

                if(part == null){
                    Eln2.LOGGER.error("Multipart at $pos part $face requested update, but was null")
                    return@forEach
                }

                val syncTag = part.getSyncTag()

                if(syncTag == null){
                    Eln2.LOGGER.error("Part $part had an update enqueued, but returned a null sync tag")
                    return@forEach
                }

                val updateTag = CompoundTag()
                updateTag.setDirection("Face", face)
                updateTag.put("SyncTag", syncTag)

                partUpdatesTag.add(updateTag)
            }

            partsRequestingUpdate.clear()
            tag.put("PartUpdates", partUpdatesTag)
        }

        return ClientboundBlockEntityDataPacket.create(this) { tag };
    }

    @ClientOnly
    override fun onDataPacket(net: Connection?, packet: ClientboundBlockEntityDataPacket?) {
        if(packet == null){
            Eln2.LOGGER.error("onDataPacket null at $pos")
            return
        }

        if(level == null){
            Eln2.LOGGER.error("onDataPacket level null at $pos")
            return
        }

        if(!level!!.isClientSide){
            Eln2.LOGGER.error("onDataPacket called on the client!")
            return
        }

        val tag = packet.tag!!
        val newPartsTag = tag.get("NewParts") as? ListTag
        val removedPartsTag = tag.get("RemovedParts") as? ListTag
        val partUpdatesTag = tag.get("PartUpdates") as? ListTag

        newPartsTag?.forEach { partTag ->
            val part = createPartFromTag(partTag as CompoundTag)

            if(parts.put(part.placementContext.face, part) != null){
                Eln2.LOGGER.error("Client received new part, but a part was already present on the ${part.placementContext.face} face!")
            }

            clientAddPart(part)
            joinCollider(part)
        }

        removedPartsTag?.forEach { faceTag ->
            val face = (faceTag as CompoundTag).getDirection("Face")

            val part = parts.remove(face)

            if(part == null){
                Eln2.LOGGER.error("Client received broken part on $face, but there was no part present on the face!")
            }
            else{
                clientRemovePart(part)
            }
        }

        partUpdatesTag?.forEach { updateTag ->
            val compound = updateTag as CompoundTag

            val face = compound.getDirection("Face")

            val part = parts[face]

            if(part == null){
                Eln2.LOGGER.error("Multipart at $pos received update on $face, but part is null!")
            }
            else{
                val syncTag = compound.get("SyncTag") as CompoundTag
                part.handleSyncTag(syncTag)
            }
        }
    }

    /**
     * Enqueues a part for renderer setup.
     * */
    @ClientOnly
    private fun clientAddPart(part : Part){
        part.onAddedToClient()
        clientUpdateQueue.add(PartUpdate(part, PartUpdateType.Add))
    }

    /**
     * Removes a part from the renderer.
     * */
    @ClientOnly
    private fun clientRemovePart(part : Part){
        part.onDestroyed()
        clientUpdateQueue.add(PartUpdate(part, PartUpdateType.Remove))
    }

    //#endregion

    //#region Disk Loading

    @ServerOnly
    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        if(level!!.isClientSide){
            return
        }

        savePartsToTag(pTag)
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

            if(pLevel.isClientSide){
                return
            }

            if(this.savedTag != null){
                Eln2.LOGGER.info("Completing multipart disk load at $pos with ${savedTag!!.size()}")
                loadPartsFromTag(savedTag!!)
                Eln2.LOGGER.info("Loaded from tag")
                parts.values.forEach { part ->
                    part.onLoaded()
                }

                Eln2.LOGGER.info("Done")

                // GC reference tracking
                savedTag = null
            }
            else{
                Eln2.LOGGER.info("Multipart save tag null")
            }
        }
        catch (ex : Exception){
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
    private fun savePartToTag(part : Part) : CompoundTag{
        val tag = CompoundTag()

        tag.setResourceLocation("ID", part.id)
        tag.putBlockPos("Pos", part.placementContext.pos)
        tag.setDirection("Face", part.placementContext.face)
        tag.setDirection("Facing", part.placementContext.horizontalFacing)

        val customTag = part.getSaveTag()

        if(customTag != null){
            tag.put("CustomTag", customTag)
        }

        return tag
    }

    /**
     * Saves the entire part set to a CompoundTag.
     * */
    @ServerOnly
    private fun savePartsToTag() : CompoundTag{
        val tag = CompoundTag()

        savePartsToTag(tag)

        return tag
    }

    /**
     * Saves the entire part set to the provided CompoundTag.
     * */
    @ServerOnly
    private fun savePartsToTag(tag : CompoundTag){
        assert(!level!!.isClientSide)

        val partsTag = ListTag()

        parts.keys.forEach { face ->
            val part = parts[face]

            partsTag.add(savePartToTag(part!!))
        }

        tag.put("Parts", partsTag)
    }

    /**
     * Loads all the parts from the tag and adds sets them up within this multipart.
     * This is used by the server to load from disk, and by the client to set up parts during the initial
     * chunk synchronization.
     * */
    private fun loadPartsFromTag(tag: CompoundTag){
        if (tag.contains("Parts")) {
            Eln2.LOGGER.info("Casting...")
            val partsTag = tag.get("Parts") as ListTag
            Eln2.LOGGER.info("Cast")
            partsTag.forEach { partTag ->
                Eln2.LOGGER.info("Part from tag...")
                val part = createPartFromTag(partTag as CompoundTag)
                Eln2.LOGGER.info("writing...")
                parts[part.placementContext.face] = part

                Eln2.LOGGER.info("Loaded $part")
            }

            rebuildCollider()

            Eln2.LOGGER.info("Deserialized all parts")
        }
        else{
            Eln2.LOGGER.warn("Multipart had no saved data")
        }
    }

    /**
     * Creates a new part using the data provided in the tag.
     * This tag should be a product of the getPartTag method.
     * This method _does not_ add the part to the part map!
     * */
    private fun createPartFromTag(tag : CompoundTag) : Part{
        val id = tag.getResourceLocation("ID")
        val pos = tag.getBlockPos("Pos")
        val face = tag.getDirection("Face")
        val facing = tag.getDirection("Facing")
        val customTag = tag.get("CustomTag") as? CompoundTag

        val provider = PartRegistry.tryGetProvider(id) ?: error("Failed to get part with id $id")
        val part = provider.create(PartPlacementContext(pos, face, facing, level!!, this))

        if(customTag != null){
            part.loadFromTag(customTag)
        }

        return part
    }

    /**
     * Gets a list of all cells within this container's parts.
     * */
    @ServerOnly
    override fun getCells(): ArrayList<CellSpaceLocation> {
        val results = ArrayList<CellSpaceLocation>()

        parts.values.forEach{ part ->
            if(part is IPartCellContainer){
                results.add(CellSpaceLocation(part.cell, part.placementContext.face))
            }
        }

        return results;
    }

    override fun query(query: CellSpaceQuery): CellSpaceLocation? {
        val part = parts[query.surface]

        if(part == null){
            Eln2.LOGGER.info("No part on face ${query.surface}")
            return null
        }

        val cellContainer = part as? IPartCellContainer

        if(cellContainer == null){
            Eln2.LOGGER.info("Part on face ${query.surface} is not a cell container")
            return null
        }

        val relativeRotation = part.getRelativeDirection(query.connectionFace)

        Eln2.LOGGER.info("${query.connectionFace} mapped to $relativeRotation")

        if(cellContainer.provider.canConnectFrom(relativeRotation)){
            Eln2.LOGGER.info("Connection accepted!")
            return CellSpaceLocation(cellContainer.cell, query.surface)
        }

        Eln2.LOGGER.info("Connection rejected")

        return null
    }

    override fun queryNeighbors(location: CellSpaceLocation): ArrayList<CellNeighborInfo> {
        val partFace = location.innerFace

        val part = parts[partFace]!!

        if(part !is IPartCellContainer){
            error("FATAL! Queried neighbors for non-cell part!")
        }

        val results = LinkedHashSet<CellNeighborInfo>()

        DirectionMask.perpendicular(partFace).forEach { searchDirection ->
            val partRelative = part.getRelativeDirection(searchDirection)

            if(!part.provider.canConnectFrom(partRelative)){
                Eln2.LOGGER.info("Part rejected connection on $partRelative")
                return@forEach
            }

            fun innerScan(){
                if(part.allowInnerConnections){
                    val innerFace = searchDirection.opposite

                    val innerPart = parts[innerFace]
                        ?: return

                    if(innerPart !is IPartCellContainer){
                        return
                    }

                    if(!innerPart.allowInnerConnections){
                        return
                    }

                    val innerRelativeRotation = innerPart.getRelativeDirection(partFace.opposite)

                    if(!innerPart.provider.canConnectFrom(innerRelativeRotation)){
                        return
                    }

                    results.add(CellNeighborInfo(CellSpaceLocation(innerPart.cell, innerPart.placementContext.face), this, partRelative, innerRelativeRotation))
                }
            }

            fun planarScan(){
                if(part.allowPlanarConnections && level!!.getBlockEntity(pos + searchDirection) is ICellContainer){
                    val remoteContainer = level!!
                        .getBlockEntity(pos + searchDirection)
                        as? ICellContainer
                        ?: return

                    val remoteConnectionFace = searchDirection.opposite

                    val remoteSpace = remoteContainer
                        .query(CellSpaceQuery(remoteConnectionFace, partFace))
                        ?: return

                    val remoteRelative = remoteContainer
                        .checkConnectionCandidate(remoteSpace, remoteConnectionFace)
                        ?: return

                    results.add(CellNeighborInfo(remoteSpace, remoteContainer, partRelative, remoteRelative))
                }
            }

            fun wrappedScan(){
                if(part.allowWrappedConnections){
                    val wrapDirection = partFace.opposite

                    val remoteContainer = level!!
                        .getBlockEntity(pos + searchDirection + wrapDirection)
                        as? ICellContainer
                        ?: return

                    val remoteSpace = remoteContainer
                        .query(CellSpaceQuery(part.placementContext.face, searchDirection))
                        ?: return

                    val remoteRelative = remoteContainer
                        .checkConnectionCandidate(remoteSpace, wrapDirection.opposite)
                        ?: return

                    results.add(CellNeighborInfo(remoteSpace, remoteContainer, partRelative, remoteRelative))
                }
            }

            innerScan()
            planarScan()
            wrappedScan()
        }

        return ArrayList(results)
    }

    override fun checkConnectionCandidate(location: CellSpaceLocation, direction: Direction): RelativeRotationDirection? {
        val part = (parts[location.innerFace]!!)
        val relative = part.getRelativeDirection(direction)

        assert(part is IPartCellContainer)

        return if((part as IPartCellContainer).provider.canConnectFrom(relative)){
            relative
        }
        else{
            null
        }
    }

    override fun recordConnection(location: CellSpaceLocation, direction: RelativeRotationDirection) {
        val part = parts[location.innerFace] as IPartCellContainer

        part.recordConnection(direction)
    }

    override fun recordDeletedConnection(location: CellSpaceLocation, direction: RelativeRotationDirection) {
        val part = parts[location.innerFace] as IPartCellContainer

        part.recordDeletedConnection(direction)
    }

    override fun topologyChanged() {
        saveData()
    }

    @ServerOnly
    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(level as? ServerLevel ?: error("Tried to get multipart cell provider on the client"))

    fun getHudMap() : Map<String, String>{
        val result = LinkedHashMap<String, String>()

        parts.values.forEach{ part ->
            if(part is IPartCellContainer){
                if(part.hasCell){
                    result[part.placementContext.face.getName()] = part.cell.graph.id.toString()

                    if(part is WirePart){
                        result["neighbors"] = part.connectedDirections.joinToString(" ")
                    }

                    result["facing"] = part.placementContext.horizontalFacing.getName()
                }
            }
        }

        return result
    }
}
