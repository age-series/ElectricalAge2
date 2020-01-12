package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.IDetail
import org.eln2.sim.electrical.mna.Node

abstract class Component: IDetail {
    abstract fun stampMatrix(c: Circuit)
    abstract fun stampRight(c: Circuit)
    abstract fun update(c: Circuit)
    // TODO: Consider adding var i: Double and var p: Double but have no backing field

    abstract var name: String
    abstract var nodes: MutableList<Node?>
}