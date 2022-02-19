package org.eln2.mc.extensions

import net.minecraft.core.Direction
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape




object VoxelShapeExtensions {
    private fun indexOfDirection(dir : Direction) : Int{
        return when(dir){
            Direction.SOUTH -> 0
            Direction.WEST -> 1
            Direction.NORTH -> 2
            Direction.EAST -> 3

            else -> error("Out of bounds!")
        }
    }

    fun VoxelShape.align(from: Direction, to: Direction): VoxelShape {
        val buffer = arrayOf(this, Shapes.empty())
        val times: Int = (indexOfDirection(to) - indexOfDirection(from) + 4) % 4

        for (i in 0 until times) {
            buffer[0].forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                buffer[1] = Shapes.or(buffer[1], Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX))
            }

            buffer[0] = buffer[1]
            buffer[1] = Shapes.empty()
        }

        return buffer[0]
    }
}
