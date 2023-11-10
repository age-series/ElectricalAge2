import net.minecraft.core.Direction
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.isHorizontal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class Base6Direction3dMaskTests {
    private val horizontalDirectionList = Base6Direction3d.values().filter { it.isHorizontal }
    private val verticalDirectionList = Base6Direction3d.values().filter { it.isVertical }

    private val horizontalsMask = Base6Direction3dMask.ofRelatives(horizontalDirectionList)
    private val verticalsMask = Base6Direction3dMask.ofRelatives(verticalDirectionList)

    @Test
    fun testVerticalsHorizontals() {
        assert(horizontalsMask.isHorizontal && horizontalsMask.hasHorizontals)
        assert(!horizontalsMask.isVertical &&!horizontalsMask.hasVerticals)

        assert(verticalsMask.hasVerticals && !verticalsMask.isHorizontal)
        assert(!verticalsMask.isHorizontal &&!verticalsMask.hasHorizontals)

        assert(Base6Direction3dMask.FULL.hasHorizontals && Base6Direction3dMask.FULL.hasVerticals &&!Base6Direction3dMask.FULL.isVertical &&!Base6Direction3dMask.FULL.isHorizontal)

        assert(!Base6Direction3dMask.EMPTY.isVertical &&! Base6Direction3dMask.EMPTY.isHorizontal);
    }

    @Test
    fun testClockwiseCounterclockwise(){
        testTransform({it.clockWise}, {if(it.isHorizontal()) it.clockWise else it}, Base6Direction3dMask.ALL_MASKS)
        testTransform({it.counterClockWise}, {if(it.isHorizontal()) it.counterClockWise else it}, Base6Direction3dMask.ALL_MASKS)

        fun stepTransform(maskTransform: (Base6Direction3dMask, Int) -> Base6Direction3dMask, directionTransform : ((Direction) -> Direction), masks: List<Base6Direction3dMask>){
            masks.forEach { untransformedMask ->
                for(i in 0..3){
                    var result = Base6Direction3dMask.EMPTY

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

        stepTransform({mask, steps -> mask.clockWise(steps)}, {it.clockWise}, Base6Direction3dMask.ALL_MASKS)
        stepTransform({mask, steps -> mask.counterClockWise(steps)}, {it.counterClockWise}, Base6Direction3dMask.ALL_MASKS)
    }

    @Test
    fun testAddRemove(){
        var mask = Base6Direction3dMask.EMPTY

        mask += Base6Direction3dMask.SOUTH + Base6Direction3dMask.NORTH

        assert(mask == (Base6Direction3dMask.SOUTH + Base6Direction3dMask.NORTH))

        mask -= Base6Direction3dMask.SOUTH + Base6Direction3dMask.NORTH

        assert(mask == Base6Direction3dMask.EMPTY)

        mask += Base6Direction3dMask.EAST

        assert(mask == Base6Direction3dMask.EAST)

        mask += Base6Direction3dMask.WEST

        assert(mask == (Base6Direction3dMask.EAST + Base6Direction3dMask.WEST))

        mask -= Base6Direction3dMask.WEST

        assert(mask == Base6Direction3dMask.EAST)

        mask -= Base6Direction3dMask.EAST

        assert(mask == Base6Direction3dMask.EMPTY)

        assert((Base6Direction3dMask.EMPTY + Base6Direction3dMask.EMPTY) == Base6Direction3dMask.EMPTY)
        assert((Base6Direction3dMask.FULL + Base6Direction3dMask.FULL) == Base6Direction3dMask.FULL)
        assert((Base6Direction3dMask.FULL - Base6Direction3dMask.FULL) == Base6Direction3dMask.EMPTY)
        assert((Base6Direction3dMask.FULL - Base6Direction3dMask.EMPTY) == Base6Direction3dMask.FULL)
        assert((Base6Direction3dMask.FULL + Base6Direction3dMask.EMPTY) == Base6Direction3dMask.FULL)
    }

    @Test
    fun testChecks(){
        Base6Direction3dMask.ALL_MASKS.forEach { mask ->
            mask.directionList.forEach { dir ->
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
        testTransform({it.opposite}, {it.opposite}, Base6Direction3dMask.ALL_MASKS)
    }

    @Test
    fun testCount(){
        Base6Direction3dMask.ALL_MASKS.forEach {
            assert(it.count == it.directionList.size)
        }
    }

    @Test
    fun testLists(){
        Base6Direction3dMask.ALL_MASKS.forEach { mask ->
            val list = ArrayList<Direction>(6)

            mask.toList(list)

            assert(list == mask.directionList)

            list.clear()

            mask.process { list.add(it) }

            assert(list == mask.directionList)
        }
    }

    private fun testTransform(
        maskTransform : ((Base6Direction3dMask) -> Base6Direction3dMask),
        directionTransform : ((Direction) -> Direction),
        mask : Base6Direction3dMask
    ){

        val transformedDirections = maskTransform(mask).directionList

        mask.directionList.forEach { untransformedDirection ->
            if(!transformedDirections.contains(directionTransform(untransformedDirection))){
                fail("Transformation failed")
            }
        }
    }

    private fun testTransform(
        maskTransform : ((Base6Direction3dMask) -> Base6Direction3dMask),
        directionTransform : ((Direction) -> Direction),
        masks : List<Base6Direction3dMask>){

        masks.forEach { testTransform(maskTransform, directionTransform, it) }
    }

    @Test
    fun testPerpendiculars(){
        Direction.values().forEach { direction ->
            val perpendiculars = Direction.values().filter { it != direction && it != direction.opposite }

            if(Base6Direction3dMask.of(perpendiculars) != Base6Direction3dMask.perpendicular(direction)){
                fail("Perpendicular test failed")
            }
        }
    }

    private fun noop(){}
}
