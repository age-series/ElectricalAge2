package org.eln2.sim.electrical
/*
import org.eln2.debug.mnaPrintln
import org.eln2.sim.electrical.component.PinsSPDT
import org.eln2.sim.electrical.component.SwitchSPDT
import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.NEGATIVE
import org.eln2.sim.electrical.mna.POSITIVE
import org.eln2.sim.electrical.mna.component.VoltageSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SwitchTestSPDT {
    class SwitchTest {
        @Test
        fun switchTest() {
            val c = Circuit()
            val sw = SwitchSPDT()
            val vs1 = VoltageSource()
            val vs2 = VoltageSource()

            c.add(vs1, vs2, sw.sw1, sw.sw2)

            vs1.connect(NEGATIVE, sw, PinsSPDT.COMMON.pin)
            vs1.connect(POSITIVE, sw, PinsSPDT.NORMALLY_CLOSED.pin)
            vs2.connect(NEGATIVE, sw, PinsSPDT.COMMON.pin)
            vs2.connect(POSITIVE, sw, PinsSPDT.NORMALLY_OPEN.pin)
            vs1.ground(NEGATIVE)

            vs1.potential = 10.0
            vs2.potential = 10.0
            sw.closedResistance = 10.0
            sw.openResistance = 100_000_000.0

            sw.closed = true

            assert(c.step(0.05))
            mnaPrintln(c)
            Assertions.assertEquals(true, (sw.sw1.current > 0.000000099) and (sw.sw1.current < 0.000000101))
            Assertions.assertEquals(true, (sw.sw2.current > 0.99) and (sw.sw2.current < 1.01))

            sw.open = true

            assert(c.step(0.05))
            mnaPrintln(c)
            Assertions.assertEquals(true, (sw.sw2.current > 0.000000099) and (sw.sw2.current < 0.000000101))
            Assertions.assertEquals(true, (sw.sw1.current > 0.99) and (sw.sw1.current < 1.01))
        }
    }
}
*/
