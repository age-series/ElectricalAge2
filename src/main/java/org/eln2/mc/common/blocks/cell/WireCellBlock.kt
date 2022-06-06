package org.eln2.mc.common.blocks.cell

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.cell.CellRegistry

class WireCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.WIRE_CELL.id
    }

    override fun getCollisionShape(pState: BlockState, pLevel: BlockGetter, pPos: BlockPos, pContext: CollisionContext): VoxelShape {
        return Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)
    }

    override fun getShape(pState: BlockState, pLevel: BlockGetter, pPos: BlockPos, pContext: CollisionContext): VoxelShape {
        return Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0)
    }
}
