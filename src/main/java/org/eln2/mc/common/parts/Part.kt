package org.eln2.mc.common.parts

import com.mojang.math.Matrix4f
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.core.Direction
import net.minecraft.core.Direction.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
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
     * It should not exceed the block size.
     * */
    abstract val baseSize : Vec3

    private var cachedShape : VoxelShape? = null

    fun getRelative(global: Direction): RelativeRotationDirection {
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
     * on the inner face.
     * */
    private val modelBoundingBox : AABB
        get() {
            return AABBUtilities
                .fromSize(baseSize)
                .transformed(facingRotation)
                .transformed(placementContext.face.rotation)
                .move(offset)
        }

    val facingRotation : Quaternion
        get() = Vector3f.YP.rotationDegrees(facingRotationDegrees)

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

    val worldBoundingBox : AABB get() = gridBoundingBox.move(Vec3(-0.5, 0.0, -0.5))

    open val shape : VoxelShape get() {
        if(cachedShape == null){
            cachedShape = Shapes.create(modelBoundingBox)
        }

        return cachedShape!!
    }

    open fun onUsedBy(entity : LivingEntity){}

    @ServerOnly
    open fun getSaveTag() : CompoundTag?{
        return null
    }

    open fun loadFromTag(tag : CompoundTag){}

    @ServerOnly
    open fun getSyncTag() : CompoundTag?{
        return null
    }

    @ClientOnly
    open fun handleSyncTag(tag : CompoundTag){}

    @ServerOnly
    fun invalidateSave(){
        if(placementContext.level.isClientSide){
            error("Cannot save on the client")
        }

        placementContext.multipart.setChanged()
    }

    @ServerOnly
    fun syncChanges(){
        if(placementContext.level.isClientSide){
            error("Cannot sync changes from client to server!")
        }

        placementContext.multipart.enqueuePartSync(placementContext.face)
    }

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
    open fun onLoaded(){}

    open fun onUnloaded(){}

    /**
     * Called when the part is destroyed.
     * */
    open fun onDestroyed(){}

    //#region Client

    @ClientOnly
    open fun onAddedToClient(){}

    @ClientOnly
    private var cachedRenderer : IPartRenderer? = null

    @ClientOnly
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

    @ClientOnly
    protected abstract fun createRenderer() : IPartRenderer

    //#endregion
}
