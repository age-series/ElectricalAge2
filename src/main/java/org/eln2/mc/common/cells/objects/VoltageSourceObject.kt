package org.eln2.mc.common.cells.objects

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.Eln2
import org.eln2.mc.common.cells.foundation.objects.ComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ConnectionInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.ResistorBundle
import org.eln2.mc.extensions.TooltipBuilderExtensions.voltageSource
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

class VoltageSourceObject : ElectricalObject(), IWailaProvider {
    private lateinit var source: VoltageSource
    private val resistors = ResistorBundle(0.1)

    override fun offerComponent(neighbour: ElectricalObject): ComponentInfo {
        return resistors.getOfferedResistor(directionOf(neighbour))
    }

    override fun recreateComponents() {
        source = VoltageSource()
        source.potential = 100.0

        resistors.clear()
    }

    override fun registerComponents(circuit: Circuit) {
        circuit.add(source)
        resistors.register(connections, circuit)
    }

    override fun build() {
        source.ground(Conventions.INTERNAL_PIN)

        resistors.connect(connections, this)
        resistors.process { it.connect(Conventions.INTERNAL_PIN, source, Conventions.EXTERNAL_PIN) }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.voltageSource(source)
    }

    override fun addConnection(connectionInfo: ConnectionInfo) {
        super.addConnection(connectionInfo)

        Eln2.LOGGER.info("VS Record connection")
    }
}
