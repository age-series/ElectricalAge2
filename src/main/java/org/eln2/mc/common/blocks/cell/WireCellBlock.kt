package org.eln2.mc.common.blocks.cell

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.common.PlacementRotation
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.CellProvider
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.extensions.LevelExtensions.getAdjacentTile
import org.eln2.mc.extensions.VoxelShapeExtensions.align
import kotlin.collections.HashMap

class WireCellBlock : CellBlockBase() {
    private val shapeCache = HashMap<BlockState, VoxelShape>()

    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.WIRE_CELL.id
    }

    private val aabb = box(7.0, 0.0, 7.0, 9.0, 1.0, 9.0)
    private val aabbConnection = box(7.0, 0.0, 0.0, 9.0, 1.0, 7.0)

    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return getFor(pState)
    }

    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return getFor(pState)
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState {
        return createConnectedBlockState(pContext.level, pContext.clickedPos, super.getStateForPlacement(pContext)!!)
    }

    override fun updateShape(
        pState: BlockState,
        pDirection: Direction,
        pNeighborState: BlockState,
        pLevel: LevelAccessor,
        pCurrentPos: BlockPos,
        pNeighborPos: BlockPos
    ): BlockState {
        return createConnectedBlockState(pLevel as Level, pCurrentPos, pState)
    }

    private fun createConnectedBlockState(level : Level, pos : BlockPos, state : BlockState) : BlockState {
        val provider = CellRegistry.registry.getValue(getCellProvider())!!

        val facing = state.getValue(FACING)
        val rotation = PlacementRotation (facing)

        fun connectionPredicate(place : PlacementRotation, dir : Direction, provider: CellProvider) : Boolean{
            val relative = place.getRelativeFromAbsolute(dir)
            if(!provider.connectableDirections.contains(relative)){
                return false
            }
            return provider.connectionPredicate(relative)
        }

        var mutState = state.setValue(north, false).setValue(south, false).setValue(east, false).setValue(west, false)

        fun getAndAdd(dir : Direction){
            val remoteTile = level.getAdjacentTile(dir, pos)
            if(remoteTile !is CellTileEntity){
                return
            }

            val remoteProvider = CellRegistry.registry.getValue(level.getBlockState(remoteTile.pos).block.registryName)!!
            val remoteRotation = PlacementRotation(remoteTile.state.getValue(FACING))
            val remoteDir = remoteRotation.getRelativeFromAbsolute(dir.opposite)
            if(connectionPredicate(rotation, dir, provider)
                && remoteProvider.connectableDirections.contains(remoteDir)
                && remoteProvider.connectionPredicate(remoteDir)){
                mutState = mutState.setValue(directionToProperty[dir]!!, true)
            }
        }

        getAndAdd(Direction.NORTH)
        getAndAdd(Direction.SOUTH)
        getAndAdd(Direction.EAST)
        getAndAdd(Direction.WEST)

        return mutState
    }

    private fun getFor(pState: BlockState) : VoxelShape {
        return shapeCache.computeIfAbsent(pState) {
            var shape = aabb
            if (pState.getValue(north)) shape = Shapes.joinUnoptimized(shape, aabbConnection, BooleanOp.OR)
            if (pState.getValue(east)) shape = Shapes.joinUnoptimized(shape, aabbConnection.align(Direction.NORTH, Direction.EAST), BooleanOp.OR)
            if (pState.getValue(south)) shape = Shapes.joinUnoptimized(shape, aabbConnection.align(Direction.NORTH, Direction.SOUTH), BooleanOp.OR)
            if (pState.getValue(west)) shape = Shapes.joinUnoptimized(shape, aabbConnection.align(Direction.NORTH, Direction.WEST), BooleanOp.OR)
            shape
        }
    }
}
