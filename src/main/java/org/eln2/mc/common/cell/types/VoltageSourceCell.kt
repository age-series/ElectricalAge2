package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.libelectric.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectToPinOf

class VoltageSourceCell(pos : BlockPos) : CellBase(pos) {
    private lateinit var source : VoltageSource

    override fun clearForRebuild() {
        source = VoltageSource()
        source.potential = 100.0
        neighbourToResistorLookup.clear()
    }

    override fun componentForNeighbour(neighbour: CellBase): ComponentInfo {
        val circuit = graph.circuit

        return ComponentInfo(neighbourToResistorLookup.computeIfAbsent(neighbour) {
            val resistor = Resistor()
            resistor.resistance = 0.001
            circuit.add(resistor)
            resistor
        }, 1)
    }

    override fun buildConnections() {
        val circuit = graph.circuit
        circuit.add(source)
        source.ground(0)

        connections.forEach { remoteCell ->
            val localResistor = componentForNeighbour(remoteCell).component
            localResistor.connectToPinOf(1,  remoteCell.componentForNeighbour(this))
            localResistor.connect(0, source, 1)
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun createDataPrint(): String {
        return "I: ${source.current}, V: ${source.potential}"
    }
}
