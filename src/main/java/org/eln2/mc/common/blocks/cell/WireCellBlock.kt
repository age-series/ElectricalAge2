package org.eln2.mc.common.blocks.cell

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.CellRegistry

class WireCellBlock : CellBlockBase() {

    lateinit var connect_east: BooleanProperty
    lateinit var connect_west: BooleanProperty
    lateinit var connect_north: BooleanProperty
    lateinit var connect_south: BooleanProperty


    init {

        registerDefaultState(stateDefinition.any().setValue(connect_east, false).setValue(connect_north, false).setValue(connect_south, false).setValue(connect_west, false))
    }


    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.WIRE_CELL.id
    }

    override fun getCollisionShape(pState: BlockState, pLevel: BlockGetter, pPos: BlockPos, pContext: CollisionContext): VoxelShape {
        return Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)
    }

    override fun getShape(pState: BlockState, pLevel: BlockGetter, pPos: BlockPos, pContext: CollisionContext): VoxelShape {
        return Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0)
    }

    override fun onPlace(pState: BlockState, pLevel: Level, pPos: BlockPos, pOldState: BlockState, pIsMoving: Boolean) {
        super.onPlace(pState, pLevel, pPos, pOldState, pIsMoving)

        // get blocks around and call nechang
        for (x in (-1 until 2)) {
            for (z in (-1 until 2)) {
                if (x == 0 && z == 0) continue
                neChang(pLevel, pPos, BlockPos(pPos.x + x, pPos.y, pPos.z + z))
            }
        }
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)

        connect_east = BooleanProperty.create("connect_east")
        connect_north = BooleanProperty.create("connect_north")
        connect_south = BooleanProperty.create("connect_south")
        connect_west = BooleanProperty.create("connect_west")

        pBuilder.add(connect_east, connect_north, connect_south, connect_west)
    }

    fun neChang(level: Level, pos: BlockPos, neighbor: BlockPos) {


        val blockEntity = level.getBlockEntity(neighbor ?: error("Neighbor position was null"))

        if(blockEntity != null) {

            // check if the blockentity is from this mod
            if(blockEntity is CellTileEntity) {
                // get the direction of the neighbor from us
                val direction = Direction.fromNormal(neighbor.subtract(pos))

                if(direction?.equals(Direction.EAST) == true) {

                    println("East")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_east, true))
                }
                if(direction?.equals(Direction.NORTH) == true) {
                    println("North")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_north, true))
                }
                if(direction?.equals(Direction.SOUTH) == true) {
                    println("South")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_south, true))
                }
                if(direction?.equals(Direction.WEST) == true) {
                    println("West")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_west, true))
                }
            } else {
                // not a cell tile entity

                val direction = Direction.fromNormal(neighbor.subtract(pos))

                println("Direction: $direction")

                if(direction?.equals(Direction.EAST) == true) {

                    println("East")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_east, false))
                }
                if(direction?.equals(Direction.NORTH) == true) {
                    println("North")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_north, false))
                }
                if(direction?.equals(Direction.SOUTH) == true) {
                    println("South")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_south, false))
                }
                if(direction?.equals(Direction.WEST) == true) {
                    println("West")
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_west, false))
                }
            }
        } else {
            // not a cell tile entity

            val direction = Direction.fromNormal(neighbor.subtract(pos))

            println("Direction: $direction")

            if(direction?.equals(Direction.EAST) == true) {

                println("East")
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_east, false))
            }
            if(direction?.equals(Direction.NORTH) == true) {
                println("North")
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_north, false))
            }
            if(direction?.equals(Direction.SOUTH) == true) {
                println("South")
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_south, false))
            }
            if(direction?.equals(Direction.WEST) == true) {
                println("West")
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(connect_west, false))
            }
        }
    }
    override fun onNeighborChange(blockState: BlockState?, world: LevelReader?, pos: BlockPos?, neighbor: BlockPos?) {

        println(world is Level)

        var level = world as Level


        if (world != null && pos != null) {
            if (!(world?.isClientSide?: error("World was null"))) {
                if (neighbor != null && neighbor != pos) {
                    neChang(level, pos, neighbor)
                }
            }
        }

        super.onNeighborChange(blockState, world, pos, neighbor)
    }
}
