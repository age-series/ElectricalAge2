import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.eln2.mc.data.*
import org.junit.jupiter.api.fail

class LocatorTests {
    @Test
    fun testLocatorSet() {
        val set = LocatorSet<Positional>()

        assert(!set.has<BlockPosLocator>())
        assert(set == set)
        assert(set.hashCode() == set.hashCode())

        val bp = BlockPos(3, 1, 4)
        val bpl = BlockPosLocator(bp)
        set.withLocator(bpl)

        assert(set.has<BlockPosLocator>())
        assert((set.get<BlockPosLocator>() ?: fail("Failed to get locator")) == bpl)

        try {
            set.withLocator(BlockPosLocator(BlockPos.ZERO))
            fail("added duplicate locator")
        }
        catch(_: Throwable) { }
    }

    @Test
    fun testLocationDescriptor() {
        val descriptor = LocationDescriptor()

        assert(!descriptor.hasLocatorSet<Positional>())
        assert(!descriptor.hasLocatorSet<Directional>())

        val bpl = BlockPosLocator(BlockPos(3, 1, 4))

        descriptor.withLocator(bpl)

        assert(descriptor.hasLocatorSet<Positional>())
        assert(!descriptor.hasLocatorSet<Directional>())

        assert(descriptor.getLocator<Positional, BlockPosLocator>() == bpl)

        try {
            descriptor.withLocator(BlockPosLocator(BlockPos.ZERO))
            fail("added duplicate locator")
        }
        catch(_: Throwable) { }

        val fl = BlockFaceLocator(Direction.DOWN)

        descriptor.withLocator(fl)
        assert(descriptor.hasLocatorSet<Directional>())
        assert(descriptor.hasLocatorSet<Positional>())

        assert(descriptor.getLocator<Directional, BlockFaceLocator>() == fl)
    }
}
