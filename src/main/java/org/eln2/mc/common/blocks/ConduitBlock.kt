package org.eln2.mc.common.blocks

import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties.*
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape


class ConduitBlock(val predicate : ((dir : Direction) -> Boolean)) : Block(Properties.of(Material.STONE)) {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false)
            .setValue(SOUTH, false)
            .setValue(EAST, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false))
    }

    private val top = 16.0
    private val bottom = 0.0
    private val c = 8.0
    private val w = 2.0
    private val smallest = c - w
    private val largest = c + w

    private val AABB = box(smallest, smallest, smallest, largest, largest, largest)
    private val AABB_UP = box(smallest, smallest, smallest, largest, top, largest)
    private val AABB_DOWN = box(smallest, bottom, smallest, largest, largest, largest)
    private val AABB_NORTH = box(smallest, smallest, bottom, largest, largest, largest)
    private val AABB_SOUTH = box(smallest, smallest, smallest, largest, largest, top)
    private val AABB_WEST = box(bottom, smallest, smallest, largest, largest, largest)
    private val AABB_EAST = box(smallest, smallest, smallest, top, largest, largest)


    private fun createShape(state: BlockState): VoxelShape {
        var shape = AABB

        if (predicate(Direction.NORTH)) shape = Shapes.joinUnoptimized(shape, AABB_NORTH, BooleanOp.OR)
        if (predicate(Direction.SOUTH)) shape = Shapes.joinUnoptimized(shape, AABB_SOUTH, BooleanOp.OR)
        if (predicate(Direction.WEST)) shape = Shapes.joinUnoptimized(shape, AABB_WEST, BooleanOp.OR)
        if (predicate(Direction.EAST)) shape = Shapes.joinUnoptimized(shape, AABB_EAST, BooleanOp.OR)
        if (predicate(Direction.UP)) shape = Shapes.joinUnoptimized(shape, AABB_UP, BooleanOp.OR)
        if (predicate(Direction.DOWN)) shape = Shapes.joinUnoptimized(shape, AABB_DOWN, BooleanOp.OR)

        return shape
    }

    override fun getRenderShape(pState: BlockState): RenderShape {
        return RenderShape.MODEL
    }




}
