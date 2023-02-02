package org.eln2.mc.common.parts

import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.core.Direction
import net.minecraft.core.Direction.AxisDirection
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.Eln2
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.blocks.MultipartBlockEntity
import org.eln2.mc.extensions.AABBExtensions.transformed
import org.eln2.mc.extensions.Vec3Extensions.div
import org.eln2.mc.utility.AABBUtilities

/**
 * Parts are entity-like units that exist in a multipart entity. They are similar to normal block entities,
 * but up to 6 can exist in the same block space.
 * They are placed on the inner faces of a multipart container block space.
 * */
abstract class Part(val id : ResourceLocation, val placementContext: PartPlacementContext) {
    /**
     * This is the size that will be used to create the bounding box for this part.
     * It should not exceed the block size.
     * */
    abstract val baseSize : Vec3

    private var cachedShape : VoxelShape? = null

    fun getRelative(global : Direction) : RelativeRotationDirection {
        return RelativeRotationDirection.fromFacingUp(placementContext.horizontalFacing, placementContext.face, global)
    }

    /**
     * This is the bounding box of the part, rotated and placed
     * on the inner face.
     * */
    val modelBoundingBox : AABB
        get() {
            // TODO: document this, it is pretty involved
            return AABBUtilities
                .fromSize(baseSize)
                .transformed(facingRotation)
                .transformed(placementContext.face.rotation)
                .move(offset)
        }

    val facingRotation : Quaternion
        get() = Vector3f.YP.rotationDegrees(facingRotationDegrees)

    private val facingRotationDegrees : Float
        get() = placementContext.horizontalFacing.toYRot()

    val offset : Vec3 get() {
        val halfSize = baseSize / 2.0

        val positiveOffset = halfSize.y
        val negativeOffset = 1 - halfSize.y

        return when(val axis = placementContext.face.axis){
            Direction.Axis.X -> Vec3((if(placementContext.face.axisDirection == AxisDirection.POSITIVE) positiveOffset else negativeOffset), 0.5, 0.5)
            Direction.Axis.Y -> Vec3(0.5, (if(placementContext.face.axisDirection == AxisDirection.POSITIVE) positiveOffset else negativeOffset), 0.5)
            Direction.Axis.Z -> Vec3(0.5, 0.5, (if(placementContext.face.axisDirection == AxisDirection.POSITIVE) positiveOffset else negativeOffset))
            else -> error("Invalid axis $axis")
        }
    }

    /**
     * This is the bounding box of the part, in its block position.
     * */
    val worldBoundingBox : AABB
        get() = modelBoundingBox.move(placementContext.pos)

    open val shape : VoxelShape get() {
        if(cachedShape == null){
            cachedShape = Shapes.create(modelBoundingBox)
        }

        return cachedShape!!
    }

    abstract fun onUsedBy(entity : LivingEntity)

    open fun getSaveTag() : CompoundTag?{
        return null
    }

    open fun loadFromTag(tag : CompoundTag){}

    open fun getSyncTag() : CompoundTag?{
        return null
    }

    open fun handleSyncTag(tag : CompoundTag){}

    fun syncChanges(){
        if(placementContext.level.isClientSide){
            error("Cannot sync changes from client to server!")
        }

        val entity = placementContext.level.getBlockEntity(placementContext.pos) as? MultipartBlockEntity

        if(entity == null){
            Eln2.LOGGER.error("FAILED TO GET MULTIPART AT ${placementContext.pos}")
            return
        }

        entity.enqueuePartSync(placementContext.face)
    }

    /**
     *  Called on the server when the part is placed.
     * */
    open fun onPlaced(){}

    /**
     * Called on the server when the part finished loading from disk
     * */
    open fun onLoaded(){}

    open fun onUnloaded(){}

    /**
     * Called when the part is destroyed.
     * */
    open fun onDestroyed(){}

    //#region Client

    open fun onAddedToClient(){}

    private var cachedRenderer : IPartRenderer? = null

    val renderer : IPartRenderer
        get(){
            if(!placementContext.level.isClientSide){
                error("Tried to get renderer on non-client side!")
            }

            if(cachedRenderer == null){
                cachedRenderer = createRenderer()
            }

            return cachedRenderer!!
        }

    protected abstract fun createRenderer() : IPartRenderer

    //#endregion
}
