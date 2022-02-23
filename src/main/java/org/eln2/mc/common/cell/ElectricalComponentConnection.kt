package org.eln2.mc.common.cell

import org.ageseries.libage.sim.electrical.mna.component.Component

// TODO: This should be abstracted by the relevant simulator with a generic to allow for more than just a Electrical component.
data class ElectricalComponentConnection(val component: Component, val index : Int)
