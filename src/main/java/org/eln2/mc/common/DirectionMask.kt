package org.eln2.mc.common

import net.minecraft.core.Direction
import java.util.*

data class DirectionMask(val mask : Int){
    val isEmpty : Boolean
        get() = (mask == 0)

    fun hasFlag(direction : Direction) : Boolean{
        val bit = getDirectionBit(direction)

        return (mask and bit) > 0
    }

    val hasDown : Boolean get() = hasFlag(Direction.DOWN)
    val hasUp : Boolean get() = hasFlag(Direction.UP)
    val hasNorth : Boolean get() = hasFlag(Direction.NORTH)
    val hasSouth : Boolean get() = hasFlag(Direction.SOUTH)
    val hasWest : Boolean get() = hasFlag(Direction.WEST)
    val hasEast : Boolean get() = hasFlag(Direction.EAST)

    override fun equals(other: Any?): Boolean {
        if(other !is DirectionMask){
            return false
        }

        return equals(other)
    }

    fun equals(other : DirectionMask) : Boolean{
        return mask == other.mask
    }

    override fun hashCode(): Int {
        return mask.hashCode()
    }

    override fun toString(): String {
        val sb = StringBuilder()

        sb.append("{")

        toList().forEach { direction ->
            sb.append(" $direction")
        }

        sb.append(" }")

        return sb.toString()
    }

    inline fun forEach(action: (Direction) -> Unit) {
        Direction.values().forEach { direction ->
            if(hasFlag(direction)){
                action(direction)
            }
        }
    }

    fun toList(results : MutableList<Direction>) {
        this.forEach {
            results.add(it)
        }
    }

    fun toList() : LinkedList<Direction>{
        val results = LinkedList<Direction>()

        toList(results)

        return results
    }

    companion object{
        fun getDirectionBit(direction: Direction) : Int{
            return when(direction){
                Direction.DOWN ->   1 shl 0
                Direction.UP ->     1 shl 1
                Direction.NORTH ->  1 shl 2
                Direction.SOUTH ->  1 shl 3
                Direction.WEST ->   1 shl 4
                Direction.EAST ->   1 shl 5
            }
        }

        val EMPTY = DirectionMask(0)

        val ALL = DirectionMask(
            Direction.values()
            .map { getDirectionBit(it) }
            .reduce {acc, b -> acc or b}
        )

        val DOWN = DirectionMask(getDirectionBit(Direction.DOWN))
        val UP = DirectionMask(getDirectionBit(Direction.UP))
        val NORTH = DirectionMask(getDirectionBit(Direction.NORTH))
        val SOUTH = DirectionMask(getDirectionBit(Direction.SOUTH))
        val WEST = DirectionMask(getDirectionBit(Direction.WEST))
        val EAST = DirectionMask(getDirectionBit(Direction.EAST))

        fun of(vararg directions : Direction) : DirectionMask{
            var result = 0

            directions.forEach { direction ->
                result = result or getDirectionBit(direction)
            }

            return DirectionMask(result)
        }

        private fun computePerpendicular(direction : Direction) : DirectionMask{
            var result = 0;

            Direction.values()
                .filter { it != direction && it != direction.opposite }
                .forEach { perpendicular ->
                    result = result or getDirectionBit(perpendicular)
                }

            return DirectionMask(result)
        }

        private val perpendiculars = Direction.values().associateWith {
            computePerpendicular(it)
        }

        fun perpendicular(direction: Direction) : DirectionMask{
            return perpendiculars[direction]!!
        }
    }
}
