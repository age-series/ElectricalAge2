package org.eln2.mc.common.blocks.cell

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.extensions.VoxelShapeExtensions.align

class `12vBatteryCellBlock`: CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.`12V_BATTERY_CELL`.id
    }

    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return getFor(pState)
    }

    // TODO: This is deprecated
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return getFor(pState)
    }

    companion object {
        private val batteryBody = box(3.0, 0.0, 2.0, 13.0, 9.0, 14.0)
        private val batteryRedLead = box(7.0, 0.0, 0.0, 9.0, 9.0, 2.0)
        private val batteryBlackLead = box(7.0, 0.0, 14.0, 9.0, 9.0, 16.0)

        private val shape = Shapes.joinUnoptimized(
            Shapes.joinUnoptimized(batteryRedLead, batteryBlackLead, BooleanOp.OR), batteryBody, BooleanOp.OR
        )

        private val cache = HashMap<BlockState, VoxelShape>()

        fun getFor(state : BlockState) : VoxelShape {
            return cache.computeIfAbsent(state){
                when(val facing = state.getValue(FACING)){
                    Direction.NORTH -> shape
                    Direction.SOUTH -> shape
                    Direction.EAST -> shape.align(Direction.NORTH, Direction.EAST)
                    Direction.WEST -> shape.align(Direction.NORTH, Direction.WEST)

                    else -> error("Unhandled dir: $facing")
                }
            }
        }
    }
}

