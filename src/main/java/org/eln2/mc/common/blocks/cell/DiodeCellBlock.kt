package org.eln2.mc.common.blocks.cell

import com.google.common.collect.ImmutableMap
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.cell.CellRegistry
import java.util.function.Function

class DiodeCellBlock: CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.DIODE_CELL.id
    }

    // TODO: This is deprecated
    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        //double pX1, double pY1, double pZ1, double pX2, double pY2, double pZ2
        return output
    }

    // TODO: This is deprecated
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return output
    }

    companion object {
        val resistorBody = box(7.0, 0.0, 0.0, 9.0, 2.0, 4.0)
        val resistorLeadLeft = box(6.0, 0.0, 4.0, 10.0, 3.0, 12.0)
        val resistorLeadRight = box(7.0, 0.0, 12.0, 9.0, 2.0, 16.0)

        val output = Shapes.joinUnoptimized(
            Shapes.joinUnoptimized(resistorLeadLeft, resistorLeadRight, BooleanOp.OR), resistorBody, BooleanOp.OR
        )
    }
}
