import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import org.eln2.mc.data.*
import org.junit.jupiter.api.fail

class LocatorTests {
    @Test
    fun testLocatorSet() {
        val set = LocatorSet()

        assert(!set.has<BlockLocator>())
        assert(set == set)
        assert(set.hashCode() == set.hashCode())

        val bp = BlockPos(3, 1, 4)
        val bpl = BlockLocator(bp)
        set.withLocator(bpl)

        assert(set.has<BlockLocator>())
        assert((set.get<BlockLocator>() ?: fail("Failed to get locator")) == bpl)

        try {
            set.withLocator(BlockLocator(BlockPos.ZERO))
            fail("added duplicate locator")
        }
        catch(_: Throwable) { }
    }
}
