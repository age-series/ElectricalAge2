package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.common.cell.ISingleElementGuiCell
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText.valueText

class VoltageSourceCell(pos: BlockPos) : CellBase(pos), ISingleElementGuiCell<Double> {

    /*  R -> local resistors. Their first pin is connected to the voltage source.
    *   C -> remote components. The second pin of the local resistors is used for them.
    *
    *       C
    *       R
    *   C R V R C
    *       R
    *       C
    */

    lateinit var source: VoltageSource private set

    override fun clearForRebuild() {
        source = VoltageSource()
        source.potential = 5.0
        neighbourToResistorLookup.clear()
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
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
            val localResistor = componentForNeighbour(remoteCell).component // get local resistor
            localResistor.connectToPinOf(1,  remoteCell.componentForNeighbour(this)) // connect local resistor to remote component
            localResistor.connect(0, source, 1) // connect local resistor to our voltage source
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun getHudMap(): Map<String, String> {
        val voltage: String = valueText(source.potential, UnitType.VOLT)
        var current: String = valueText(0.0, UnitType.AMPERE)
        var power: String = valueText(0.0, UnitType.WATT)
        val map = mutableMapOf<String, String>()

        try {
            current = valueText(source.current, UnitType.AMPERE)
            power = valueText(source.potential * source.current, UnitType.WATT)
        } catch (_: Exception) {
            // No results from simulator
        }

        map["voltage"] = voltage
        map["current"] = current
        map["power"] = power

        return map
    }

    override fun getGuiValue(): Double = source.potential

    override fun setGuiValue(value: Double) {
        source.potential = value
    }
}
