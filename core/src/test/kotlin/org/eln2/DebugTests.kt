package org.eln2

import org.eln2.debug.dprint
import org.eln2.debug.dprintln
import org.junit.jupiter.api.Test

internal class DebugTests {
    @Test
    fun dprintTest() {
        dprint("foo+", true)
        dprintln("bar=", false)
        dprintln("foobar")
    }
}
