package org.eln2.mc.common.content

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.common.cells.foundation.ComponentHolder
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject

class GeneratorObject : ElectricalObject() {
    var internalResistance: Double = 1.0
        set(value){
            field = value

            resistor.ifPresent {
                it.resistance = value
            }
        }

    var potential: Double = 1.0
        set(value){
            field = value

            source.ifPresent {
                it.potential = value
            }
        }

    private val resistor = ComponentHolder {
        Resistor().also { it.resistance = internalResistance }
    }

    private val source = ComponentHolder {
        VoltageSource().also { it.potential = potential }
    }

    override val maxConnections = 1

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistor.offerExternal()
    }

    override fun clearComponents() {
        resistor.clear()
        source.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor.instance)
        circuit.add(source.instance)
    }

    override fun build() {
        resistor.connectInternal(source.offerExternal())

        source.groundInternal()

        if(connections.size == 0){
            return
        }

        val connectionInfo = connections[0].obj.offerComponent(this)

        resistor.connectExternal(connectionInfo)
    }
}
