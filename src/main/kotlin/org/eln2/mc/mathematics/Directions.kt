package org.eln2.mc.mathematics

import net.minecraft.core.Direction
import org.eln2.mc.alias
import org.eln2.mc.index
import org.eln2.mc.isHorizontal
import org.eln2.mc.isVertical
import org.joml.Matrix4f

enum class RelativeDir(val id: Int) {
    Front(1),
    Back(2),
    Left(3),
    Right(4),
    Up(5),
    Down(6);

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

    companion object {
        /**
         * Computes the Relative Rotation Direction from a global direction.
         * @param facing The forward direction of the object.
         * @param normal The up direction of the object.
         * @param direction The global direction.
         * @return The global direction, mapped to the relative direction, in the object's frame.
         * */
        fun fromForwardUp(facing: Direction, normal: Direction, direction: Direction): RelativeDir {
            if (facing.isVertical()) {
                error("Facing cannot be vertical")
            }

            if (direction == normal) {
                return Up
            }

            if (direction == normal.opposite) {
                return Down
            }

            val adjustedFacing = Direction.rotate(Matrix4f().set(normal.rotation), facing)

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

            return result
        }

        fun fromId(id: Int): RelativeDir {
            return when (id) {
                Front.id -> Front
                Back.id -> Back
                Left.id -> Left
                Right.id -> Right
                Up.id -> Up
                Down.id -> Down

                else -> error("Invalid local direction id: $id")
            }
        }
    }
}

/**
 * The Direction Mask is used to manipulate up to 6 directions at the same time.
 *  - The Directions are stored in a Bit Mask.
 *  - All important operations are fully cached.
 *  - Support for *RelativeRotationDirection* and Minecraft *Direction*
 * */
@JvmInline
value class DirectionMask(val mask: Int) {
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
        val EMPTY = DirectionMask(0)

        /**
         * Gets a Direction Mask with all 6 directions in it.
         * */
        val FULL = DirectionMask(
            Direction.values()
                .map { getBit(it) }
                .reduce { acc, b -> acc or b }
        )

        // This cache maps single directions to masks.
        private val perDirection = Direction.values()
            .sortedBy { it.index() }
            .map { DirectionMask(getBit(it)) }
            .toTypedArray()

        // P.S these are not overloads because I had some strange issues caused by overloads.
        // Maybe fix it in the future?

        /**
         * Gets the cached mask for the specified direction.
         * */
        fun of(direction: Direction): DirectionMask {
            return perDirection[direction.index()]
        }

        /**
         * Gets the cached mask for the specified direction.
         * */
        fun ofRelative(direction: RelativeDir): DirectionMask {
            return of(direction.alias)
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun of(directions: List<Direction>): DirectionMask {
            if (directions.isEmpty()) {
                return EMPTY
            }

            return DirectionMask(directions
                .map { getBit(it) }
                .reduce { acc, mask -> acc or mask }
            )
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun of(vararg directions: Direction): DirectionMask {
            return of(directions.asList())
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun ofRelatives(directions: List<RelativeDir>): DirectionMask {
            if (directions.isEmpty()) {
                return EMPTY
            }

            return DirectionMask(directions
                .map { getBit(it.alias) }
                .reduce { acc, mask -> acc or mask }
            )
        }

        /**
         * **Computes** the mask for the specified directions.
         * */
        fun ofRelatives(vararg directions: RelativeDir): DirectionMask {
            return ofRelatives(directions.asList())
        }

        val DOWN = of(Direction.DOWN)
        val UP = of(Direction.UP)

        val NORTH = of(Direction.NORTH)
        val FRONT = ofRelative(RelativeDir.Front)

        val SOUTH = of(Direction.SOUTH)
        val BACK = ofRelative(RelativeDir.Back)

        val WEST = of(Direction.WEST)
        val LEFT = ofRelative(RelativeDir.Left)

        val EAST = of(Direction.EAST)
        val RIGHT = ofRelative(RelativeDir.Right)

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

                return@map DirectionMask(result)
            }
            .toTypedArray()

        /**
         * Gets the cached mask with perpendicular directions to the specified direction.
         * */
        fun perpendicular(direction: Direction): DirectionMask {
            return perpendiculars[direction.index()]
        }

        // These are all mask combinations.
        private val allMasks = (0..FULL.mask)
            .map { DirectionMask(it) }
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
        }
            .toTypedArray()

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
    val index get() = mask

    /**
     * Computes a mask that contains the horizontal components of this mask.
     * This computation uses a single AND, so it is quite cheap.
     * */
    val horizontalComponent get() = DirectionMask(mask and HORIZONTALS.mask)

    /**
     * Computes a mask that contains the vertical components of this mask.
     * This computation uses a single AND, so it is quite cheap.
     * */
    val verticalComponent get() = DirectionMask(mask and VERTICALS.mask)

    //#region Checks

    /**
     * @return True if this mask is empty (no directions are stored in it). Otherwise, false.
     * */
    val isEmpty get() = (mask == 0)

    /**
     * @return True if this mask has any directions stored in it. Otherwise, false.
     * */
    val isNotEmpty get() = !isEmpty

    /**
     * @return True if this mask has the specified direction stored in it. Otherwise, false.
     * */
    fun hasFlag(direction: Direction): Boolean {
        return (mask and getBit(direction)) > 0
    }

    /**
     * @return True if this mask has the specified direction stored in it. Otherwise, false.
     * */
    fun hasFlag(direction: RelativeDir): Boolean {
        return hasFlag(direction.alias)
    }

    /**
     * Checks if this mask contains all the directions in the specified mask.
     * *This is not a check for equality!*
     *
     * @return True, if this mask contains all directions specified in the parameter. Otherwise, false.
     * */
    fun hasFlags(flags: DirectionMask): Boolean {
        return (mask and flags.mask) == flags.mask
    }

    /**
     * Checks if this mask contains any of the directions in the specified mask.
     *
     * @return True, if this mask contains at least one of the directions in the specified mask. Otherwise, false. Also, Empty masks will always return false.
     * */
    fun hasAnyFlags(flags: DirectionMask): Boolean {
        return (mask and flags.mask) > 0
    }

    infix fun has(direction: Direction): Boolean {
        return hasFlag(direction)
    }

    infix fun has(direction: RelativeDir): Boolean {
        return hasFlag(direction)
    }

    infix fun has(flags: DirectionMask): Boolean {
        return hasFlags(flags)
    }

    //#endregion

    //#region Inlined checks

    /**
     * Checks if this mask contains any vertical directions.
     * */
    val hasVerticals get() = hasAnyFlags(VERTICALS)

    /**
     * Checks if this mask contains any horizontal directions.
     * */
    val hasHorizontals get() = hasAnyFlags(HORIZONTALS)

    /**
     * Checks if this mask contains **only** vertical directions. Empty masks will return false.
     * */
    val isVertical get() = hasVerticals && !hasHorizontals

    /**
     * Checks if this mask contains **only** horizontal directions. Empty masks will return false.
     * */
    val isHorizontal get() = hasHorizontals && !hasVerticals

    val hasDown get() = hasFlag(Direction.DOWN)
    val hasUp get() = hasFlag(Direction.UP)
    val hasNorth get() = hasFlag(Direction.NORTH)
    val hasFront get() = hasFlag(RelativeDir.Front)
    val hasSouth get() = hasFlag(Direction.SOUTH)
    val hasBack get() = hasFlag(RelativeDir.Back)
    val hasWest get() = hasFlag(Direction.WEST)
    val hasLeft get() = hasFlag(RelativeDir.Left)
    val hasEast get() = hasFlag(Direction.EAST)
    val hasRight get() = hasFlag(RelativeDir.Right)

    //#endregion

    /**
     * Gets the cached list of directions in this mask.
     * */
    val directionList get() = directionLists[this.mask]

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
    fun transformed(transform: ((Direction) -> Direction)): DirectionMask {
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
    fun transformed(transform: ((Direction) -> Direction), filter: ((Direction) -> Boolean)): DirectionMask {
        if (isEmpty) {
            // REQUIRED
            return EMPTY
        }

        return of(directionList.map { if (filter(it)) transform(it) else it })
    }

    /**
     * Gets the cached clockwise mask. Vertical directions are left unaffected.
     * */
    val clockWise get() = clockwiseMasks[this.mask]

    /**
     * Gets the cached counterclockwise mask. Vertical directions are left unaffected.
     * */
    val counterClockWise get() = counterClockwiseMasks[this.mask]

    /**
     * Gets the cached mask, with all directions inverted.
     * */
    val opposite get() = oppositeMasks[this.mask]

    // P.S. when we use OPPOSITE here, we ensure that vertical directions don't get included.
    // We want these APIs to not affect vertical directions, and OPPOSITE would.
    // To do so, we get the opposite of the horizontal components, then combine that with the original vertical components

    /**
     * Gets the cached mask, rotated clockwise by the specified number of steps. Vertical directions are left unaffected.
     * */
    fun clockWise(steps: Int): DirectionMask {
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
    fun counterClockWise(steps: Int): DirectionMask {
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
    fun matchClockwise(targetMask: DirectionMask): Int {
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
    fun matchCounterClockWise(targetMask: DirectionMask): Int {
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

    /**
     * Gets the number of directions in this mask, using a bit operation.
     * @see Int.countOneBits
     * */
    val count get() = mask.countOneBits()

    /**
     * Combines the directions of the two masks into one mask.
     * */
    operator fun plus(other: DirectionMask): DirectionMask {
        return DirectionMask(this.mask or other.mask)
    }

    /**
     * Combines the directions of the two masks into one mask.
     * */
    operator fun plus(direction: Direction): DirectionMask {
        return DirectionMask(this.mask or getBit(direction))
    }

    /**
     * Creates a mask without the specified directions.
     * */
    operator fun minus(direction: Direction): DirectionMask {
        return DirectionMask(this.mask and getBit(direction).inv())
    }

    /**
     * Creates a mask without the specified directions.
     * */
    operator fun minus(mask: DirectionMask): DirectionMask {
        return DirectionMask(this.mask and mask.mask.inv())
    }
}
