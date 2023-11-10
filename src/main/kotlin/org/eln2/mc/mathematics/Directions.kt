@file:Suppress("NOTHING_TO_INLINE")

package org.eln2.mc.mathematics

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import net.minecraft.core.Direction
import org.eln2.mc.alias
import org.eln2.mc.data.ImmutableByteArrayView
import org.eln2.mc.index
import org.eln2.mc.isHorizontal
import org.eln2.mc.isVertical

enum class Base6Direction3d(val id: Int) {
    Front(0),
    Back(1),
    Left(2),
    Right(3),
    Up(4),
    Down(5);

    /**
     * Gets the opposite of this direction.
     * */
    val opposite
        get() = when (this) {
            Front -> Back
            Back -> Front
            Left -> Right
            Right -> Left
            Up -> Down
            Down -> Up
        }

    /**
     * @return True, if this direction is a horizontal direction.
     * */
    val isHorizontal get() = this != Up && this != Down

    /**
     * @return True, if this direction is a vertical direction.
     * */
    val isVertical get() = this == Up || this == Down

    operator fun plus(other: Base6Direction3d) = Base6Direction3dMask.ofRelative(this) + Base6Direction3dMask.ofRelative(other)
    operator fun plus(other: Base6Direction3dMask) = other + Base6Direction3dMask.ofRelative(this)

    companion object {
        private val FROM_FORWARD_UP = let {
            val map = Int2IntOpenHashMap()

            for (facing in Direction.values()) {
                if(facing.isVertical()) {
                    continue
                }

                for (normal in Direction.values()) {
                    for (direction in Direction.values()) {
                        val result = when (direction) {
                            normal -> {
                                Up
                            }

                            normal.opposite -> {
                                Down
                            }

                            else -> {
                                val adjustedFacing = Direction.rotate(com.mojang.math.Matrix4f(normal.rotation), facing)

                                var result = when (direction) {
                                    adjustedFacing -> Front
                                    adjustedFacing.opposite -> Back
                                    adjustedFacing.getClockWise(normal.axis) -> Right
                                    adjustedFacing.getCounterClockWise(normal.axis) -> Left
                                    else -> error("Adjusted facing did not match")
                                }

                                if (normal.axisDirection == Direction.AxisDirection.NEGATIVE) {
                                    if (result == Left || result == Right) {
                                        result = result.opposite
                                    }
                                }

                                result
                            }
                        }

                        map.put(BlockPosInt.pack(facing.index(), normal.index(), direction.index()), result.id)
                    }
                }
            }

            map
        }

        fun fromForwardUp(facing: Direction, normal: Direction, direction: Direction): Base6Direction3d {
            if (facing.isVertical()) {
                error("Facing cannot be vertical")
            }

            return Base6Direction3d.byId[
                FROM_FORWARD_UP.get(
                    BlockPosInt.pack(
                        facing.index(),
                        normal.index(),
                        direction.index()
                    )
                )
            ]
        }

        val byId = values().toList()
    }
}

@JvmInline
value class Base6Direction3dMask(val value: Int) {
    companion object {
        /**
         * Gets the mask bit associated with the specified direction.
         * */
        fun getBit(direction: Direction): Int {
            return when (direction) {
                Direction.NORTH -> 1 shl 0
                Direction.SOUTH -> 1 shl 1
                Direction.WEST -> 1 shl 2
                Direction.EAST -> 1 shl 3
                Direction.DOWN -> 1 shl 4
                Direction.UP -> 1 shl 5
            }
        }

        /**
         * Gets a Direction Mask without any directions in it.
         * */
        val EMPTY = Base6Direction3dMask(0)

        /**
         * Gets a Direction Mask with all 6 directions in it.
         * */
        val FULL = Base6Direction3dMask(
            Direction.values()
                .map { getBit(it) }
                .reduce { acc, b -> acc or b }
        )

        // This cache maps single directions to masks.
        private val perDirection = Direction.values()
            .sortedBy { it.index() }
            .map { Base6Direction3dMask(getBit(it)) }
            .toTypedArray()

        // P.S these are not overloads because I had some strange issues caused by overloads.
        // Maybe fix it in the future?

        /**
         * Gets the cached mask for the specified direction.
         * */
        fun of(direction: Direction): Base6Direction3dMask {
            return perDirection[direction.index()]
        }

        /**
         * Gets the cached mask for the specified direction.
         * */
        fun ofRelative(direction: Base6Direction3d): Base6Direction3dMask {
            return of(direction.alias)
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun of(directions: List<Direction>): Base6Direction3dMask {
            if (directions.isEmpty()) {
                return EMPTY
            }

            return Base6Direction3dMask(directions
                .map { getBit(it) }
                .reduce { acc, mask -> acc or mask }
            )
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun of(vararg directions: Direction): Base6Direction3dMask {
            return of(directions.asList())
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun ofRelatives(directions: List<Base6Direction3d>): Base6Direction3dMask {
            if (directions.isEmpty()) {
                return EMPTY
            }

            return Base6Direction3dMask(directions
                .map { getBit(it.alias) }
                .reduce { acc, mask -> acc or mask }
            )
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun ofRelatives(vararg directions: Base6Direction3d): Base6Direction3dMask {
            return ofRelatives(directions.asList())
        }

        val DOWN = of(Direction.DOWN)
        val UP = of(Direction.UP)

        val NORTH = of(Direction.NORTH)
        val FRONT = ofRelative(Base6Direction3d.Front)

        val SOUTH = of(Direction.SOUTH)
        val BACK = ofRelative(Base6Direction3d.Back)

        val WEST = of(Direction.WEST)
        val LEFT = ofRelative(Base6Direction3d.Left)

        val EAST = of(Direction.EAST)
        val RIGHT = ofRelative(Base6Direction3d.Right)

        val HORIZONTALS = NORTH + SOUTH + EAST + WEST
        val VERTICALS = UP + DOWN

        // This cache maps single directions to masks with the 4 perpendicular directions.
        private val perpendiculars = Direction.values()
            .map { direction ->
                var result = 0

                Direction.values()
                    .sortedBy { it.index() }
                    .filter { it != direction && it != direction.opposite }
                    .forEach { perpendicular ->
                        result = result or getBit(perpendicular)
                    }

                return@map Base6Direction3dMask(result)
            }
            .toTypedArray()

        /**
         * Gets the cached mask with perpendicular directions to the specified direction.
         * */
        fun perpendicular(direction: Direction): Base6Direction3dMask {
            return perpendiculars[direction.index()]
        }

        // These are all mask combinations.
        private val allMasks = (0..FULL.value)
            .map { Base6Direction3dMask(it) }
            .toList()
            .toTypedArray()

        /**
         * Gets the cached list of all masks.
         * */
        val ALL_MASKS = allMasks.toList()

        // This cache maps masks to lists of directions.
        private val directionLists = allMasks.map { mask ->
            val list = ArrayList<Direction>()
            mask.toList(list)
            return@map list.toList()
        }.toTypedArray()

        // We precompute the most common transformations here.
        // I chose to allow all masks to be transformed using clockwise/counterclockwise.
        // Vertical directions will be unaffected by those transformations.

        private fun horizontalFilter(dir: Direction): Boolean {
            return dir.isHorizontal()
        }

        // This cache maps masks to masks with clockwise rotated directions.
        private val clockwiseMasks = allMasks
            .map { mask -> mask.transformed({ dir -> dir.clockWise }, Companion::horizontalFilter) }
            .toTypedArray()

        // This cache maps masks to masks with counterclockwise rotated directions.
        private val counterClockwiseMasks = allMasks
            .map { mask -> mask.transformed({ dir -> dir.counterClockWise }, Companion::horizontalFilter) }
            .toTypedArray()

        // This cache maps masks to masks with opposite directions.
        private val oppositeMasks = allMasks
            .map { mask -> mask.transformed { dir -> dir.opposite } }
            .toTypedArray()
    }

    /**
     * Gets the index used in all caches.
     * This actually corresponds to the mask itself.
     * */
    val index get() = value

    val horizontalComponent get() = Base6Direction3dMask(value and HORIZONTALS.value)
    val verticalComponent get() = Base6Direction3dMask(value and VERTICALS.value)
    val isEmpty get() = (value == 0)
    val isNotEmpty get() = !isEmpty
    fun hasFlag(direction: Direction) = (value and getBit(direction)) > 0
    fun hasFlag(direction: Base6Direction3d) = hasFlag(direction.alias)
    fun hasAll(flags: Base6Direction3dMask) = (value and flags.value) == flags.value
    fun hasAny(flags: Base6Direction3dMask) = (value and flags.value) > 0

    infix fun has(direction: Direction) = hasFlag(direction)
    infix fun has(direction: Base6Direction3d) = hasFlag(direction)
    infix fun has(flags: Base6Direction3dMask) = hasAll(flags)

    //#endregion

    val hasVerticals get() = hasAny(VERTICALS)
    val hasHorizontals get() = hasAny(HORIZONTALS)
    val isVertical get() = hasVerticals && !hasHorizontals
    val isHorizontal get() = hasHorizontals && !hasVerticals
    val hasDown get() = hasFlag(Direction.DOWN)
    val hasUp get() = hasFlag(Direction.UP)
    val hasNorth get() = hasFlag(Direction.NORTH)
    val hasFront get() = hasFlag(Base6Direction3d.Front)
    val hasSouth get() = hasFlag(Direction.SOUTH)
    val hasBack get() = hasFlag(Base6Direction3d.Back)
    val hasWest get() = hasFlag(Direction.WEST)
    val hasLeft get() = hasFlag(Base6Direction3d.Left)
    val hasEast get() = hasFlag(Direction.EAST)
    val hasRight get() = hasFlag(Base6Direction3d.Right)

    /**
     * Gets the cached list of directions in this mask.
     * */
    val directionList get() = directionLists[this.value]

    override fun toString(): String {
        val sb = StringBuilder()

        sb.append("{")

        directionList.forEach { direction ->
            sb.append(" $direction")
        }

        sb.append(" }")

        return sb.toString()
    }

    /**
     * Calls a consumer function for every direction in this mask.
     * */
    inline fun process(action: (Direction) -> Unit) {
        Direction.values().forEach { direction ->
            if (hasFlag(direction)) {
                action(direction)
            }
        }
    }

    //#region Transformations

    /**
     * Computes a mask using the directions in this mask, transformed via the specified transform function.
     * */
    fun transformed(transform: ((Direction) -> Direction)): Base6Direction3dMask {
        if (isEmpty) {
            // REQUIRED
            return EMPTY
        }

        return of(directionList.map { transform(it) })
    }

    /**
     * Computes a mask using the directions in this mask, transformed via the specified transform function.
     * Only directions that match the filter are transformed. If a direction does not match the filter, it is left unaffected.
     * */
    fun transformed(transform: ((Direction) -> Direction), filter: ((Direction) -> Boolean)): Base6Direction3dMask {
        if (isEmpty) {
            // REQUIRED
            return EMPTY
        }

        return of(directionList.map { if (filter(it)) transform(it) else it })
    }

    /**
     * Gets the cached clockwise mask. Vertical directions are left unaffected.
     * */
    val clockWise get() = clockwiseMasks[this.value]

    /**
     * Gets the cached counterclockwise mask. Vertical directions are left unaffected.
     * */
    val counterClockWise get() = counterClockwiseMasks[this.value]

    /**
     * Gets the cached mask, with all directions inverted.
     * */
    val opposite get() = oppositeMasks[this.value]

    // P.S. when we use OPPOSITE here, we ensure that vertical directions don't get included.
    // We want these APIs to not affect vertical directions, and OPPOSITE would.
    // To do so, we get the opposite of the horizontal components, then combine that with the original vertical components

    /**
     * Gets the cached mask, rotated clockwise by the specified number of steps. Vertical directions are left unaffected.
     * */
    fun clockWise(steps: Int): Base6Direction3dMask {
        return when (val remainder = steps % 4) {
            0 -> this
            1 -> this.clockWise
            2 -> this.horizontalComponent.opposite + this.verticalComponent
            3 -> this.counterClockWise
            else -> error("Unexpected remainder $remainder")
        }
    }

    /**
     * Gets the cached mask, rotated counterclockwise by the specified number of steps. Vertical directions are left unaffected.
     * */
    fun counterClockWise(steps: Int): Base6Direction3dMask {
        return when (val remainder = steps % 4) {
            0 -> this
            1 -> this.counterClockWise
            2 -> this.horizontalComponent.opposite + verticalComponent
            3 -> this.clockWise
            else -> error("Unexpected remainder $remainder")
        }
    }

    //#endregion

    /**
     * Tries to match the source mask to this mask, by rotating it clockwise a number of steps.
     * @return The number of steps needed to match the 2 masks, or -1 if no match was found.
     * */
    fun matchClockwise(targetMask: Base6Direction3dMask): Int {
        for (i in 0..3) {
            val rotated = this.clockWise(i)

            if (targetMask == rotated) {
                return i
            }
        }

        return -1
    }

    /**
     * Tries to match the source mask to this mask, by rotating it counterclockwise a number of steps.
     * @return The number of steps needed to match the 2 masks, or -1 if no match was found.
     * */
    fun matchCounterClockWise(targetMask: Base6Direction3dMask): Int {
        for (i in 0..3) {
            val rotated = this.counterClockWise(i)

            if (targetMask == rotated) {
                return i
            }
        }

        return -1
    }

    /**
     * Populates the specified list with the directions in this mask.
     * */
    fun toList(results: MutableList<Direction>) {
        this.process {
            results.add(it)
        }
    }

    val count get() = value.countOneBits()

    operator fun plus(other: Base6Direction3dMask) = Base6Direction3dMask(this.value or other.value)
    operator fun plus(direction: Direction) = Base6Direction3dMask(this.value or getBit(direction))
    operator fun plus(direction: Base6Direction3d) = Base6Direction3dMask(this.value or getBit(direction.alias))
    operator fun minus(direction: Direction) = Base6Direction3dMask(this.value and getBit(direction).inv())
    operator fun minus(direction: Base6Direction3d) = Base6Direction3dMask(this.value and getBit(direction.alias).inv())
    operator fun minus(mask: Base6Direction3dMask) = Base6Direction3dMask(this.value and mask.value.inv())
}


/**
 * Gets the x, y, z components in order from [Direction.values]
 * */
val DIRECTION_COMPONENTS = Direction.values().let {
    val result = ArrayList<Byte>()

    it.forEach { direction ->
        result.add(direction.stepX.toByte())
        result.add(direction.stepY.toByte())
        result.add(direction.stepZ.toByte())
    }

    ImmutableByteArrayView(result.toByteArray())
}

inline fun directionIncrementX(dir3dDataValue: Int) = DIRECTION_COMPONENTS[dir3dDataValue * 3 + 0]
inline fun directionIncrementY(dir3dDataValue: Int) = DIRECTION_COMPONENTS[dir3dDataValue * 3 + 1]
inline fun directionIncrementZ(dir3dDataValue: Int) = DIRECTION_COMPONENTS[dir3dDataValue * 3 + 2]

