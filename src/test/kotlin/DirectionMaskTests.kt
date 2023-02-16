import net.minecraft.core.Direction
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.DirectionExtensions.isHorizontal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class DirectionMaskTests {
    private val horizontalDirectionList = RelativeRotationDirection.values().filter { it.isHorizontal }
    private val verticalDirectionList = RelativeRotationDirection.values().filter { it.isVertical }

    private val horizontalsMask = DirectionMask.ofRelatives(horizontalDirectionList)
    private val verticalsMask = DirectionMask.ofRelatives(verticalDirectionList)

    @Test
    fun testVerticalsHorizontals() {
        assert(horizontalsMask.isHorizontal && horizontalsMask.hasHorizontals)
        assert(!horizontalsMask.isVertical &&!horizontalsMask.hasVerticals)

        assert(verticalsMask.hasVerticals && !verticalsMask.isHorizontal)
        assert(!verticalsMask.isHorizontal &&!verticalsMask.hasHorizontals)

        assert(DirectionMask.FULL.hasHorizontals && DirectionMask.FULL.hasVerticals &&!DirectionMask.FULL.isVertical &&!DirectionMask.FULL.isHorizontal)

        assert(!DirectionMask.EMPTY.isVertical &&! DirectionMask.EMPTY.isHorizontal);
    }

    @Test
    fun testClockwiseCounterclockwise(){
        testTransform({it.clockWise}, {if(it.isHorizontal()) it.clockWise else it}, DirectionMask.ALL_MASKS)
        testTransform({it.counterClockWise}, {if(it.isHorizontal()) it.counterClockWise else it}, DirectionMask.ALL_MASKS)

        fun stepTransform(maskTransform: (DirectionMask, Int) -> DirectionMask, directionTransform : ((Direction) -> Direction), masks: List<DirectionMask>){
            masks.forEach { untransformedMask ->
                for(i in 0..3){
                    var result = DirectionMask.EMPTY

                    untransformedMask.directionList.forEach { direction ->
                        var rotatedDirection = direction

                        if(direction.isHorizontal()){

                            for(steps in 0 until i){
                                rotatedDirection = directionTransform(rotatedDirection)
                            }
                        }

                        result += rotatedDirection
                    }

                    val transformedMask = maskTransform(untransformedMask, i)

                    noop()

                    if(transformedMask != result){
                        fail("Step transform failed")
                    }
                }
            }
        }

        stepTransform({mask, steps -> mask.clockWise(steps)}, {it.clockWise}, DirectionMask.ALL_MASKS)
        stepTransform({mask, steps -> mask.counterClockWise(steps)}, {it.counterClockWise}, DirectionMask.ALL_MASKS)
    }

    @Test
    fun testAddRemove(){
        var mask = DirectionMask.EMPTY

        mask += DirectionMask.SOUTH + DirectionMask.NORTH

        assert(mask == (DirectionMask.SOUTH + DirectionMask.NORTH))

        mask -= DirectionMask.SOUTH + DirectionMask.NORTH

        assert(mask == DirectionMask.EMPTY)

        mask += DirectionMask.EAST

        assert(mask == DirectionMask.EAST)

        mask += DirectionMask.WEST

        assert(mask == (DirectionMask.EAST + DirectionMask.WEST))

        mask -= DirectionMask.WEST

        assert(mask == DirectionMask.EAST)

        mask -= DirectionMask.EAST

        assert(mask == DirectionMask.EMPTY)

        assert((DirectionMask.EMPTY + DirectionMask.EMPTY) == DirectionMask.EMPTY)
        assert((DirectionMask.FULL + DirectionMask.FULL) == DirectionMask.FULL)
        assert((DirectionMask.FULL - DirectionMask.FULL) == DirectionMask.EMPTY)
        assert((DirectionMask.FULL - DirectionMask.EMPTY) == DirectionMask.FULL)
        assert((DirectionMask.FULL + DirectionMask.EMPTY) == DirectionMask.FULL)
    }

    @Test
    fun testChecks(){
        DirectionMask.ALL_MASKS.forEach { mask ->
            Direction.values().forEach { dir ->
                assert(mask.has(dir) == mask.directionList.contains(dir))

                if(dir == Direction.NORTH) assert(mask.hasFront && mask.hasNorth)
                if(dir == Direction.SOUTH) assert(mask.hasBack && mask.hasSouth)
                if(dir == Direction.EAST) assert(mask.hasRight && mask.hasEast)
                if(dir == Direction.WEST) assert(mask.hasLeft && mask.hasWest)
                if(dir == Direction.UP) assert(mask.hasUp)
                if(dir == Direction.DOWN) assert(mask.hasDown)

            }
        }
    }

    @Test
    fun testOpposite(){
        testTransform({it.opposite}, {it.opposite}, DirectionMask.ALL_MASKS)
    }

    @Test
    fun testCount(){
        DirectionMask.ALL_MASKS.forEach {
            assert(it.count == it.directionList.size)
        }
    }

    @Test
    fun testLists(){
        DirectionMask.ALL_MASKS.forEach { mask ->
            val list = ArrayList<Direction>(6)

            mask.toList(list)

            assert(list == mask.directionList)

            list.clear()

            mask.process { list.add(it) }

            assert(list == mask.directionList)
        }
    }

    private fun testTransform(
        maskTransform : ((DirectionMask) -> DirectionMask),
        directionTransform : ((Direction) -> Direction),
        mask : DirectionMask
    ){

        val transformedDirections = maskTransform(mask).directionList

        mask.directionList.forEach { untransformedDirection ->
            if(!transformedDirections.contains(directionTransform(untransformedDirection))){
                fail("Transformation failed")
            }
        }
    }

    private fun testTransform(
        maskTransform : ((DirectionMask) -> DirectionMask),
        directionTransform : ((Direction) -> Direction),
        masks : List<DirectionMask>){

        masks.forEach { testTransform(maskTransform, directionTransform, it) }
    }

    @Test
    fun testPerpendiculars(){
        Direction.values().forEach { direction ->
            val perpendiculars = Direction.values().filter { it != direction && it != direction.opposite }

            if(DirectionMask.of(perpendiculars) != DirectionMask.perpendicular(direction)){
                fail("Perpendicular test failed")
            }
        }
    }

    private fun noop(){}
}
