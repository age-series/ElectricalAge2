package org.eln2.mc.common.space

import net.minecraft.core.Direction
import org.eln2.mc.extensions.DirectionExtensions.directionAlias
import org.eln2.mc.extensions.DirectionExtensions.index
import org.eln2.mc.extensions.DirectionExtensions.isHorizontal

@JvmInline
value class DirectionMask(val mask: Int) {
    companion object {
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

        val EMPTY = DirectionMask(0)

        val FULL = DirectionMask(
            Direction.values()
                .map { getBit(it) }
                .reduce { acc, b -> acc or b }
        )

        private val perDirection = Direction.values()
            .sortedBy { it.index() }
            .map { DirectionMask(getBit(it)) }
            .toTypedArray()

        fun of(direction: Direction): DirectionMask {
            return perDirection[direction.index()]
        }

        fun ofRelative(direction: RelativeRotationDirection): DirectionMask {
            return of(direction.directionAlias())
        }

        fun of(directions: List<Direction>): DirectionMask {
            if (directions.isEmpty()) {
                return EMPTY
            }

            return DirectionMask(directions
                .map { getBit(it) }
                .reduce { acc, mask -> acc or mask }
            )
        }

        fun of(vararg directions: Direction): DirectionMask {
            return of(directions.asList())
        }

        fun ofRelatives(directions: List<RelativeRotationDirection>): DirectionMask {
            if (directions.isEmpty()) {
                return EMPTY
            }

            return DirectionMask(directions
                .map { getBit(it.directionAlias()) }
                .reduce { acc, mask -> acc or mask }
            )
        }

        fun ofRelatives(vararg directions: RelativeRotationDirection): DirectionMask {
            return ofRelatives(directions.asList())
        }

        val DOWN = of(Direction.DOWN)
        val UP = of(Direction.UP)

        val NORTH = of(Direction.NORTH)
        val FRONT = ofRelative(RelativeRotationDirection.Front)

        val SOUTH = of(Direction.SOUTH)
        val BACK = ofRelative(RelativeRotationDirection.Back)

        val WEST = of(Direction.WEST)
        val LEFT = ofRelative(RelativeRotationDirection.Left)

        val EAST = of(Direction.EAST)
        val RIGHT = ofRelative(RelativeRotationDirection.Right)

        val HORIZONTALS = NORTH + SOUTH + EAST + WEST
        val VERTICALS = UP + DOWN

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

        fun perpendicular(direction: Direction): DirectionMask {
            return perpendiculars[direction.index()]
        }

        private val allMasks = (0..FULL.mask)
            .map { DirectionMask(it) }
            .toList()
            .toTypedArray();

        val ALL_MASKS = allMasks.toList()

        private val directionLists = allMasks.        // Pre-compute lists of Direction for lookup
        map { mask ->
            val list = ArrayList<Direction>()
            mask.toList(list)
            return@map list.toList()
        }
            .toTypedArray()

        /**
         * Applies the specified transform function to the mask's directions and returns the transformed mask.
         * */

        // We precompute the most common transformations here.
        // I chose to allow all masks to be transformed using clockwise/counterclockwise.
        // Vertical directions will be unaffected by those transformations.

        private fun horizontalFilter(dir: Direction): Boolean {
            return dir.isHorizontal();
        }

        private val clockwiseMasks = allMasks
            .map { mask -> mask.transformed({ dir -> dir.clockWise }, Companion::horizontalFilter) }
            .toTypedArray()

        private val counterClockwiseMasks = allMasks
            .map { mask -> mask.transformed({ dir -> dir.counterClockWise }, Companion::horizontalFilter) }
            .toTypedArray()

        private val oppositeMasks = allMasks
            .map { mask -> mask.transformed { dir -> dir.opposite } }
            .toTypedArray()
    }

    val index get() = mask

    val horizontalComponent get() = DirectionMask(mask and HORIZONTALS.mask)
    val verticalComponent get() = DirectionMask(mask and VERTICALS.mask)

    //#region Checks

    val isEmpty get() = (mask == 0)
    val isNotEmpty get() = !isEmpty

    fun hasFlag(direction: Direction): Boolean {
        return (mask and getBit(direction)) > 0
    }

    fun hasFlag(direction: RelativeRotationDirection): Boolean {
        return hasFlag(direction.directionAlias())
    }

    fun hasFlags(flags: DirectionMask): Boolean {
        return (mask and flags.mask) == flags.mask
    }

    fun hasAnyFlags(flags: DirectionMask): Boolean {
        return (mask and flags.mask) > 0
    }

    infix fun has(direction: Direction): Boolean {
        return hasFlag(direction)
    }

    infix fun has(direction: RelativeRotationDirection): Boolean {
        return hasFlag(direction)
    }

    infix fun has(flags: DirectionMask): Boolean {
        return hasFlags(flags)
    }

    //#endregion

    //#region Inlined checks

    val hasVerticals get() = hasAnyFlags(VERTICALS)
    val hasHorizontals get() = hasAnyFlags(HORIZONTALS)
    val isVertical get() = hasVerticals && !hasHorizontals
    val isHorizontal get() = hasHorizontals && !hasVerticals

    val hasDown get() = hasFlag(Direction.DOWN)
    val hasUp get() = hasFlag(Direction.UP)
    val hasNorth get() = hasFlag(Direction.NORTH)
    val hasFront get() = hasFlag(RelativeRotationDirection.Front)
    val hasSouth get() = hasFlag(Direction.SOUTH)
    val hasBack get() = hasFlag(RelativeRotationDirection.Back)
    val hasWest get() = hasFlag(Direction.WEST)
    val hasLeft get() = hasFlag(RelativeRotationDirection.Left)
    val hasEast get() = hasFlag(Direction.EAST)
    val hasRight get() = hasFlag(RelativeRotationDirection.Right)

    //#endregion

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

    inline fun process(action: (Direction) -> Unit) {
        Direction.values().forEach { direction ->
            if (hasFlag(direction)) {
                action(direction)
            }
        }
    }

    //#region Transformations

    private fun transformed(transform: ((Direction) -> Direction)): DirectionMask {
        if (isEmpty) {
            // REQUIRED
            return EMPTY
        }

        return of(directionList.map { transform(it) })
    }

    /**
     * Applies the specified transform function to the mask's directions, if they match the filter, and returns the transformed mask.
     * If they do not match the filter, the direction remains unaffected.
     * */
    fun transformed(transform: ((Direction) -> Direction), filter: ((Direction) -> Boolean)): DirectionMask {
        if (isEmpty) {
            // REQUIRED
            return EMPTY
        }

        return of(directionList.map { if (filter(it)) transform(it) else it })
    }

    val clockWise get() = clockwiseMasks[this.mask]
    val counterClockWise get() = counterClockwiseMasks[this.mask]
    val opposite get() = oppositeMasks[this.mask]

    // P.S. when we use OPPOSITE here, we ensure that vertical directions don't get included.
    // We want these APIs to not affect vertical directions, and OPPOSITE would.
    // To do so, we get the opposite of the horizontal components, then combine that with the original vertical components

    fun clockWise(steps: Int): DirectionMask {
        return when (val remainder = steps % 4) {
            0 -> this
            1 -> this.clockWise
            2 -> this.horizontalComponent.opposite + this.verticalComponent
            3 -> this.counterClockWise
            else -> error("Unexpected remainder $remainder")
        }
    }

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

    fun toList(results: MutableList<Direction>) {
        this.process {
            results.add(it)
        }
    }

    val count get() = mask.countOneBits()

    operator fun plus(other: DirectionMask): DirectionMask {
        return DirectionMask(this.mask or other.mask)
    }

    operator fun plus(direction: Direction): DirectionMask {
        return DirectionMask(this.mask or getBit(direction))
    }

    operator fun minus(direction: Direction): DirectionMask {
        return DirectionMask(this.mask and getBit(direction).inv())
    }

    operator fun minus(mask: DirectionMask): DirectionMask {
        return DirectionMask(this.mask and mask.mask.inv())
    }
}
