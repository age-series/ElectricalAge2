package org.eln2.mc.common.cells

import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

class `12VBatteryCell`(pos: CellPos) : CellBase(pos) {

    /*
    *   V -> local voltage source.
    *   C -> remote components.
    *
    *   C V C
    */

    private lateinit var source: VoltageSource

    override fun clear() {
        source = VoltageSource()
        source.potential = 12.5
        neighbourToResistorLookup.clear()
    }

    // TODO: Battery needs a + and - terminal. Currently, it's just implicitly grounded because I can't get that to work.

    override fun getOfferedComponent(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit

        return ComponentInfo(neighbourToResistorLookup.computeIfAbsent(neighbour) {
            val resistor = Resistor()
            resistor.resistance = 0.001
            circuit.add(resistor)
            resistor
        }, 1) // pin 1 will be connected to the remote cell
    }

    override fun buildConnections() {
        val circuit = graph.circuit
        circuit.add(source)
        source.ground(0)

        connections.forEach { remoteCell ->
            val localResistor = getOfferedComponent(remoteCell).component // get local resistor
            localResistor.connectToPinOf(
                1,
                remoteCell.getOfferedComponent(this)
            ) // connect local resistor to remote component
            localResistor.connect(0, source, 1) // connect local resistor to our voltage source
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun getHudMap(): Map<String, String> {
        val voltage: String = ValueText.valueText(source.potential, UnitType.VOLT)
        var current: String = ValueText.valueText(0.0, UnitType.AMPERE)
        var power: String = ValueText.valueText(0.0, UnitType.WATT)
        val map = mutableMapOf<String, String>()

        try {
            current = ValueText.valueText(source.current, UnitType.AMPERE)
            power = ValueText.valueText(source.potential * source.current, UnitType.WATT)
        } catch (_: Exception) {
            // No results from simulator
        }

        map["voltage"] = voltage
        map["current"] = current
        map["power"] = power

        return map
    }
}
