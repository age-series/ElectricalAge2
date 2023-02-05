package org.eln2.mc.common.parts

import com.mojang.math.Matrix4f
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.core.Direction
import net.minecraft.core.Direction.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.Eln2
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.extensions.AABBExtensions.transformed
import org.eln2.mc.extensions.Vec3Extensions.div
import org.eln2.mc.utility.AABBUtilities
import org.eln2.mc.utility.ClientOnly
import org.eln2.mc.utility.ServerOnly

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
        val res = RelativeRotationDirection.fromForwardUp(
            placementContext.horizontalFacing,
            placementContext.face,
            global
        )

        val adjustedFacing = Direction.rotate(Matrix4f(placementContext.face.rotation), placementContext.horizontalFacing)

        Eln2.LOGGER.info("Adj facing: $adjustedFacing")


        return res
    }

    /**
     * This is the bounding box of the part, rotated and placed
     * on the inner face. It is not translated to the position of the part in the world (it is a local frame)
     * */
    private val modelBoundingBox : AABB
        get() = AABBUtilities
            .fromSize(baseSize)
            .transformed(facingRotation)
            .transformed(placementContext.face.rotation)
            .move(offset)

    /**
     * This gets the local Y rotation due to facing.
     * */
    val facingRotation : Quaternion
        get() = Vector3f.YP.rotationDegrees(facingRotationDegrees)

    /**
     * This calculates the local Y rotation degrees due to facing.
     * */
    private val facingRotationDegrees : Float
        get(){
            val offset = 0

            return offset + when(placementContext.horizontalFacing){
                NORTH -> 0f
                SOUTH -> 180f
                WEST -> 90f
                EAST -> -90f
                else -> error("Invalid horizontal facing ${placementContext.horizontalFacing}")
            }
        }

    private val offset : Vec3 get() {
        val halfSize = baseSize / 2.0

        val positiveOffset = halfSize.y
        val negativeOffset = 1 - halfSize.y

        return when(val axis = placementContext.face.axis){
            Axis.X -> Vec3((if(placementContext.face.axisDirection == AxisDirection.POSITIVE) positiveOffset else negativeOffset), 0.5, 0.5)
            Axis.Y -> Vec3(0.5, (if(placementContext.face.axisDirection == AxisDirection.POSITIVE) positiveOffset else negativeOffset), 0.5)
            Axis.Z -> Vec3(0.5, 0.5, (if(placementContext.face.axisDirection == AxisDirection.POSITIVE) positiveOffset else negativeOffset))
            else -> error("Invalid axis $axis")
        }
    }

    /**
     * This is the bounding box of the part, in its block position.
     * */
    val gridBoundingBox : AABB get() = modelBoundingBox.move(placementContext.pos)

    /**
     * This is the bounding box of the part, in final world coordinates.
     * */
    val worldBoundingBox : AABB get() = gridBoundingBox.move(Vec3(-0.5, 0.0, -0.5))

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
     * Called when this part is being unloaded.
     * */
    open fun onUnloaded(){}

    /**
     * Called when the part is destroyed (broken).
     * */
    open fun onDestroyed(){}

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
