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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import org.eln2.mc.Eln2
import org.eln2.mc.common.parts.Part
import org.eln2.mc.common.parts.PartProvider
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.extensions.AABBExtensions.viewClip
import org.eln2.mc.extensions.NbtExtensions.getBlockPos
import org.eln2.mc.extensions.NbtExtensions.getDirection
import org.eln2.mc.extensions.NbtExtensions.getResourceLocation
import org.eln2.mc.extensions.NbtExtensions.putBlockPos
import org.eln2.mc.extensions.NbtExtensions.setDirection
import org.eln2.mc.extensions.NbtExtensions.setResourceLocation
import org.eln2.mc.extensions.Vec3Extensions.minus
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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

    // Used for streaming to clients
    private val newParts = ArrayList<Part>()

    // used for disk loading
    private var savedTag : CompoundTag? = null

    private val boundingBox : AABB get(){
        return AABB(pos)
    }

    init {
        Eln2.LOGGER.info("Constructed multipart block entity $this")
    }

    override fun setRemoved() {
        Eln2.LOGGER.info("Multipart block entity removed")

        super.setRemoved()
    }

    fun pickPart(entity : Player) : Part?{
        // I don't like what I did here very much.
        // also does not work very well. todo

        parts.values
            .sortedBy { (entity.eyePosition - it.boundingBox.center).lengthSqr() }
            .forEach { part ->
                val aabb = part.boundingBox

                val intersection = aabb.viewClip(entity)

                if(!intersection.isEmpty){
                    return part
                }
            }

        return null
    }

    fun place(entity: Player, pos : BlockPos, face : Direction, provider : PartProvider) : Boolean{
        if(entity.level.isClientSide){
            Eln2.LOGGER.fatal("MULTIPART PLACE CALLED ON CLIENT")
            return false
        }

        val level = entity.level as ServerLevel

        Eln2.LOGGER.info("Part placing on $face")

        if(parts.containsKey(face)){
            Eln2.LOGGER.error("Already have a part there!")
            return false
        }

        val part = provider.create(pos, face, level)

        assert(parts.put(face, part) == null)

        newParts.add(part)

        Eln2.LOGGER.info("Successfully placed part!")

        // to test picking:

        val picked = pickPart(entity)

        Eln2.LOGGER.info("Picked: $picked, expected: $part")

        setChanged()

        level.sendBlockUpdated(blockPos, state, state, Block.UPDATE_CLIENTS)

        Eln2.LOGGER.info("Sent some update things")

        // send part packets updates

        return true
    }

    //#region Client Chunk Synchronization

    // The following methods get called when chunks are first synchronized to clients
    // Here, we send all the parts we have.

    override fun getUpdateTag(): CompoundTag {
        if(level!!.isClientSide){
            error("getUpdateTag called on client!")
        }

        return savePartsToTag()
    }

    override fun handleUpdateTag(tag: CompoundTag?) {
        if(tag == null){
            Eln2.LOGGER.error("Update tag NULL!")
            return
        }

        if(level == null){
            Eln2.LOGGER.error("Level was null in handleUpdateTag!")
            return
        }

        if(!level!!.isClientSide){
            Eln2.LOGGER.error("handleUpdateTag called on server!")
            return
        }

        loadPartsFromTag(tag)

        parts.values.forEach { part ->
            part.onAddedToClient()
        }
    }

    //#endregion

    //#region Client Streaming

    // The following methods get called by our code (through forge), after we add new parts
    // Here, we send the freshly placed parts to clients that are already observing this multipart.

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        if(newParts.size == 0){
            Eln2.LOGGER.error("getUpdatePacket new parts list is empty!")
            return null
        }

        val tag = CompoundTag()
        val newPartsTag = ListTag()

        newParts.forEach { part ->
            newPartsTag.add(savePartToTag(part))
        }

        Eln2.LOGGER.info("Serialized ${newParts.size} new parts")

        newParts.clear()

        tag.put("NewParts", newPartsTag)

        return ClientboundBlockEntityDataPacket.create(this) { tag };
    }

    override fun onDataPacket(net: Connection?, pkt: ClientboundBlockEntityDataPacket?) {
        if(pkt == null){
            Eln2.LOGGER.error("onDataPacket null!")
            return
        }

        if(level == null){
            Eln2.LOGGER.error("onDataPacket level null!")
            return
        }

        if(!level!!.isClientSide){
            Eln2.LOGGER.error("onDataPacket called on the client!")
            return
        }

        val tag = pkt.tag!!
        val newPartsTag = tag.get("NewParts") as? ListTag

        if(newPartsTag == null){
            Eln2.LOGGER.error("onDataPacket new parts tag was null!")
            return
        }

        newPartsTag.forEach{partTag ->
            val part = createPartFromTag(partTag as CompoundTag)

            if(parts.put(part.face, part) != null){
                Eln2.LOGGER.warn("Client received new part, but a part was already present on the ${part.face} face!")
            }

            part.onAddedToClient()

            Eln2.LOGGER.info("Deserialized ${part.id}")
        }
    }

    //#endregion

    //#region Disk Loading

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        savePartsToTag(pTag)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        Eln2.LOGGER.info("Loaded multipart tag")

        savedTag = pTag
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(this.savedTag != null){
            Eln2.LOGGER.info("Completing disk load")
            loadPartsFromTag(savedTag!!)
            savedTag = null
        }
    }

    //#endregion

    private fun savePartToTag(part : Part) : CompoundTag{
        val tag = CompoundTag()

        tag.setResourceLocation("ID", part.id)
        tag.putBlockPos("Pos", part.pos)
        tag.setDirection("Face", part.face)

        val customTag = part.getCustomTag()

        if(customTag != null){
            tag.put("CustomTag", customTag)
        }

        return tag
    }

    private fun savePartsToTag() : CompoundTag{
        val tag = CompoundTag()

        savePartsToTag(tag)

        return tag
    }

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

                parts[part.face] = part

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

        val part = provider.create(pos, face, level!!)

        if(customTag != null){
            part.useCustomTag(customTag)
        }

        return part
    }
}
