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
import org.eln2.mc.Eln2
import org.eln2.mc.common.parts.Part
import org.eln2.mc.common.parts.PartPlacementContext
import org.eln2.mc.common.parts.PartProvider
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.extensions.NbtExtensions.getBlockPos
import org.eln2.mc.extensions.NbtExtensions.getDirection
import org.eln2.mc.extensions.NbtExtensions.getResourceLocation
import org.eln2.mc.extensions.NbtExtensions.putBlockPos
import org.eln2.mc.extensions.NbtExtensions.setDirection
import org.eln2.mc.extensions.NbtExtensions.setResourceLocation
import org.eln2.mc.utility.AABBUtilities
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Multipart entities
 *  - Are dummy entities, that do not have any special data or logic by themselves
 *  - Act as a container for Parts. There may be one part per inner face (maximum of 6 parts per multipart entity)
 *  - The player can place inside the multipart entity. Placement and breaking logic must be emulated.
 *  - The multipart entity saves data for all the parts. Parts are responsible for their rendering.
 * */
class MultipartBlockEntity (var pos : BlockPos, var state: BlockState) :
    BlockEntity(BlockRegistry.MULTIPART_BLOCK_ENTITY.get(), pos, state) {

    private val parts = HashMap<Direction, Part>()

    // Used for streaming to clients:

    private val newParts = ArrayList<Part>()
    // We should not store references to destroyed parts.
    // All we need for removal is the face.
    private val removedParts = ArrayList<Direction>()

    // used for disk loading
    private var savedTag : CompoundTag? = null

    // Used for rendering:
    val clientNewPartQueue = ConcurrentLinkedQueue<Part>()
    val clientRemovedQueue = ConcurrentLinkedQueue<Part>()

    private fun invalidate(){
        setChanged()
        level!!.sendBlockUpdated(blockPos, state, state, Block.UPDATE_CLIENTS)
    }

    override fun setRemoved() {
        super.setRemoved()

        Eln2.LOGGER.info("Multipart block entity removed at $pos")
    }

    fun pickPart(entity : LivingEntity) : Part?{
        return AABBUtilities.clipScene(entity, { it.worldBoundingBox }, parts.values)
    }

    fun place(entity: Player, pos : BlockPos, face : Direction, provider : PartProvider) : Boolean{
        if(entity.level.isClientSide){
            return false
        }

        val level = entity.level as ServerLevel

        Eln2.LOGGER.info("Part placing on $face at $pos")

        if(parts.containsKey(face)){
            return false
        }

        val part = provider.create(PartPlacementContext(pos, face, level))

        parts[face] = part

        newParts.add(part)

        Eln2.LOGGER.info("Part placement completed.")

        invalidate()

        return true
    }

    /**
     * Tries to break the part picked by the player.
     * @return True if there are no parts left after breaking and, as such, this entity should be destroyed.
     * */
    fun remove(entity : Player, level : Level, pos : BlockPos) : Boolean{
        if(level.isClientSide){
            return false
        }

        val part = pickPart(entity) ?: return false

        parts.remove(part.placementContext.face)
        removedParts.add(part.placementContext.face)

        part.onDestroyed()

        invalidate()

        return parts.size == 0
    }

    //#region Client Chunk Synchronization

    // The following methods get called when chunks are first synchronized to clients
    // Here, we send all the parts we have.

    override fun getUpdateTag(): CompoundTag {
        return savePartsToTag()
    }

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
    }

    //#endregion

    //#region Client Streaming

    // The following methods get called by our code (through forge), after we add new parts
    // Here, we send the freshly placed parts to clients that are already observing this multipart.

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        if(newParts.size == 0 && removedParts.size == 0){
            Eln2.LOGGER.error("getUpdatePacket changed list is empty at $pos")
            return null
        }

        val tag = CompoundTag()

        val newPartsTag = ListTag()
        newParts.forEach { part ->
            newPartsTag.add(savePartToTag(part))
        }

        newParts.clear()

        val removedPartsTag = ListTag()
        removedParts.forEach { face ->
            val faceTag = CompoundTag()
            faceTag.setDirection("Face", face)
            removedPartsTag.add(faceTag)
        }
        removedParts.clear()

        tag.put("NewParts", newPartsTag)
        tag.put("RemovedParts", removedPartsTag)

        return ClientboundBlockEntityDataPacket.create(this) { tag };
    }

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

        newPartsTag?.forEach { partTag ->
            val part = createPartFromTag(partTag as CompoundTag)

            if(parts.put(part.placementContext.face, part) != null){
                Eln2.LOGGER.error("Client received new part, but a part was already present on the ${part.placementContext.face} face!")
            }

            clientAddPart(part)
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
    }

    // Enqueues a part for rendering set-up.
    private fun clientAddPart(part : Part){
        part.onAddedToClient()
        clientNewPartQueue.add(part)
    }

    // Dequeues a part for rendering set-up.
    private fun clientRemovePart(part : Part){
        part.onDestroyed()
        clientRemovedQueue.add(part)
    }

    //#endregion

    //#region Disk Loading

    override fun saveAdditional(pTag: CompoundTag) {
        Eln2.LOGGER.info("Saving additional...")
        savePartsToTag(pTag)
        Eln2.LOGGER.info("Done saving")
    }

    override fun load(pTag: CompoundTag) {
        savedTag = pTag

        // This is necessary because we don't have the level at this stage
        // We need the level to load the parts
        // Loading will complete in setLevel
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(this.savedTag != null){
            Eln2.LOGGER.info("Completing multipart disk load at $pos")
            loadPartsFromTag(savedTag!!)

            // GC reference tracking
            savedTag = null
        }
    }

    //#endregion

    /**
     * Saves all the data associated with a part to a CompoundTag.
     * */
    private fun savePartToTag(part : Part) : CompoundTag{
        val tag = CompoundTag()

        tag.setResourceLocation("ID", part.id)
        tag.putBlockPos("Pos", part.placementContext.pos)
        tag.setDirection("Face", part.placementContext.face)

        val customTag = part.getCustomTag()

        if(customTag != null){
            tag.put("CustomTag", customTag)
        }

        return tag
    }

    /**
     * Saves the entire part set to a CompoundTag.
     * */
    private fun savePartsToTag() : CompoundTag{
        val tag = CompoundTag()

        savePartsToTag(tag)

        return tag
    }

    /**
     * Saves the entire part set to the provided CompoundTag.
     * */
    private fun savePartsToTag(tag : CompoundTag){
        val partsTag = ListTag()

        parts.keys.forEach { face ->
            val part = parts[face]

            partsTag.add(savePartToTag(part!!))
        }

        tag.put("Parts", partsTag)
    }

    /**
     * Loads all the parts from the tag and adds them to the map.
     * */
    private fun loadPartsFromTag(tag: CompoundTag){
        if (tag.contains("Parts")) {
            val partsTag = tag.get("Parts") as ListTag

            partsTag.forEach { partTag ->
                val part = createPartFromTag(partTag as CompoundTag)

                parts[part.placementContext.face] = part

                Eln2.LOGGER.info("Loaded $part")
            }

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
        val customTag = tag.get("CustomTag") as? CompoundTag

        val provider = PartRegistry.tryGetProvider(id) ?: error("Failed to get part with id $id")

        val part = provider.create(PartPlacementContext(pos, face, level!!))

        if(customTag != null){
            part.useCustomTag(customTag)
        }

        return part
    }
}
