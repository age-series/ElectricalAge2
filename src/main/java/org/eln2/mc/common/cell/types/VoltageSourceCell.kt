package org.eln2.mc.common.cell.types

import net.minecraft.core.BlockPos
import org.eln2.libelectric.sim.electrical.mna.Circuit
import org.eln2.libelectric.sim.electrical.mna.component.Component
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.libelectric.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.ComponentInfo
import org.eln2.mc.extensions.ComponentExtensions.connectTo

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
            val r = Resistor()
            r.resistance = 0.001
            if(!circuit.components.contains(r)){
                circuit.add(r)
                // attention! o(1) lookup required todo!
            }
            r
        }, 1)
    }

    override fun buildConnections() {
        val circuit = graph.circuit
        circuit.add(source)
        source.ground(0)

        connections.forEach { remoteCell ->
            val localResistor = componentForNeighbour(remoteCell).component
            val remoteComponentInfo = remoteCell.componentForNeighbour(this)
            localResistor.connectTo(1,  remoteComponentInfo)
            localResistor.connect(0, source, 1)
        }
    }

    private val neighbourToResistorLookup = HashMap<CellBase, Resistor>()

    override fun createDataPrint(): String {
        return "I: ${source.current}, V: ${source.potential}"
    }
}
