package org.eln2.mc.common.parts.foundation

import com.mojang.math.Quaternion
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.annotations.ClientOnly
import org.eln2.mc.annotations.ServerOnly

/**
 * Parts are entity-like units that exist in a multipart entity. They are similar to normal block entities,
 * but up to 6 can exist in the same block space.
 * They are placed on the inner faces of a multipart container block space.
 * */
abstract class Part(val id : ResourceLocation, val placementContext: PartPlacementContext) {
    /**
     * This is the size that will be used to create the bounding box for this part.
     * It should not exceed the block size, but that is not enforced.
     * */
    abstract val baseSize : Vec3

    private var cachedShape : VoxelShape? = null

    /**
     * This gets the relative direction towards the global direction, taking into account the facing of this part.
     * @param global A global direction.
     * @return The relative direction towards the global direction.
     * */
    fun getRelativeDirection(global: Direction): RelativeRotationDirection {
        return PartTransformations.getRelativeRotation(placementContext.horizontalFacing, placementContext.face, global)
    }

    /**
     * This is the bounding box of the part, rotated and placed
     * on the inner face. It is not translated to the position of the part in the world (it is a local frame)
     * */
    private val modelBoundingBox : AABB
        get() = PartTransformations.modelBoundingBox(baseSize, placementContext.horizontalFacing, placementContext.face)

    /**
     * This gets the local Y rotation due to facing.
     * */
    val facingRotation : Quaternion
        get() = PartTransformations.facingRotation(placementContext.horizontalFacing)

    /**
     * This calculates the local Y rotation degrees due to facing.
     * */
    private val facingRotationDegrees : Float
        get() = PartTransformations.facingRotationDegrees(placementContext.horizontalFacing)

    private val offset : Vec3 get() = PartTransformations.offset(baseSize, placementContext.face)

    /**
     * This is the bounding box of the part, in its block position.
     * */
    val gridBoundingBox : AABB get() = PartTransformations.gridBoundingBox(
        baseSize,
        placementContext.horizontalFacing,
        placementContext.face,
        placementContext.pos
    )

    /**
     * This is the bounding box of the part, in final world coordinates.
     * */
    val worldBoundingBox : AABB get() = PartTransformations.worldBoundingBox(
        baseSize,
        placementContext.horizontalFacing,
        placementContext.face,
        placementContext.pos
    )

    /**
     * Gets the shape of this part. Used for block highlighting and collisions.
     * The default implementation creates a shape from the model bounding box and caches it.
     * */
    open val shape : VoxelShape get() {
        if(cachedShape == null){
            cachedShape = Shapes.create(modelBoundingBox)
        }

        return cachedShape!!
    }

    /**
     * Called when the part is right-clicked by a living entity.
     * */
    open fun onUsedBy(context: PartUseContext) : InteractionResult{
        return InteractionResult.SUCCESS
    }

    /**
     * This method is used for saving the part.
     * @return A compound tag with all the save data for this part, or null, if no data needs saving.
     * */
    @ServerOnly
    open fun getSaveTag() : CompoundTag?{
        return null
    }

    /**
     * This method is called to restore the part data from the compound tag.
     * This method is used on both logical sides. The client only receives this call
     * when the initial chunk synchronization happens.
     * @param tag The custom data tag, as created by getSaveTag.
     * */
    open fun loadFromTag(tag : CompoundTag){}

    /**
     * This method is called when this part is invalidated, and in need of synchronization to clients.
     * You will receive this tag in handleSyncTag on the client, _if_ the tag is not null.
     * @return A compound tag with all part updates. You may return null, but that might indicate an error in logic.
     * This method is called only when an update is _requested_, so there should be data in need of synchronization.
     *
     * */
    @ServerOnly
    open fun getSyncTag() : CompoundTag?{
        return null
    }

    /**
     * This method is called on the client after the server logic of this part requested an update, and the update was received.
     * @param tag The custom data tag, as returned by the getSyncTag method on the server.
     * */
    @ClientOnly
    open fun handleSyncTag(tag : CompoundTag){}

    /**
     * This method invalidates the saved data of the part.
     * This ensures that the part will be saved to the disk.
     * */
    @ServerOnly
    fun invalidateSave(){
        if(placementContext.level.isClientSide){
            error("Cannot save on the client")
        }

        placementContext.multipart.setChanged()
    }

    /**
     * This method synchronizes all changes from the server to the client.
     * It calls the getSyncTag (server) / handleSyncTag(client) combo.
     * */
    @ServerOnly
    fun syncChanges(){
        if(placementContext.level.isClientSide){
            error("Cannot sync changes from client to server!")
        }

        placementContext.multipart.enqueuePartSync(placementContext.face)
    }

    /**
     * This method invalidates the saved data and synchronizes to clients.
     * @see invalidateSave
     * @see syncChanges
     * */
    @ServerOnly
    fun syncAndSave(){
        syncChanges()
        invalidateSave()
    }

    /**
     *  Called on the server when the part is placed.
     * */
    @ServerOnly
    open fun onPlaced(){}

    /**
     * Called on the server when the part finished loading from disk
     * */
    @ServerOnly
    open fun onLoaded(){}

    /**
     * Called when this part is added to a multipart.
     * */
    open fun onAdded(){}

    /**
     * Called when this part is being unloaded.
     * */
    open fun onUnloaded(){}

    /**
     * Called when the part is destroyed (broken).
     * */
    open fun onBroken(){}

    /**
     * Called when the part is removed from the multipart.
     * */
    open fun onRemoved(){}

    @ClientOnly
    open fun onAddedToClient(){}

    @ClientOnly
    private var cachedRenderer : IPartRenderer? = null

    /**
     * Gets the renderer instance for this part.
     * By default, it calls the createRenderer method, and caches the result.
     * */
    @ClientOnly
    open val renderer : IPartRenderer
        get(){
            if(!placementContext.level.isClientSide){
                error("Tried to get renderer on non-client side!")
            }

            if(cachedRenderer == null){
                cachedRenderer = createRenderer()
            }

            return cachedRenderer!!
        }

    /**
     * Creates a renderer instance for this part.
     * @return A new instance of the part renderer.
     * */
    @ClientOnly
    abstract fun createRenderer() : IPartRenderer

    @ClientOnly
    open fun destroyRenderer(){
        cachedRenderer?.remove()
        cachedRenderer = null
    }
}
