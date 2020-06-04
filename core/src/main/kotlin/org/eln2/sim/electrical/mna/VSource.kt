package org.eln2.sim.electrical.mna

import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.VoltageSource

/**
 * A voltage source in a [Circuit].
 *
 * This is not the [Component]; like [Node], this is a support class for a [Circuit], and users generally shouldn't have to deal with these except as an implementation detail of Components. If you're looking to [Circuit.add] a voltage source to a Circuit instead, use the [VoltageSource] Component, which is much more ergonomic than this.
 *
 * These are typically constructed by the [Circuit] for [Component]s which request them via [Component.vsCount].
 *
 * Unlike [Node]s, they do not connect; they are, however, an important independent part of the MNA calculation, and are assigned indices into the Circuit matrices (which they share with Nodes; the VSource indices are offset by the number of Nodes).
 *
 * As part of the output step of the MNA, [current] is set.
 */
class VSource(var circuit: Circuit) : IDetail {
    /**
     * The current through this VSource, assigned as the output of MNA, in Amperes.
     *
     * Note that this current is measured counter to the sign of the potential, which means you might want to invert it.
     */
    var current: Double = 0.0

    /**
     * The index of this VSource amonst other VSources in a [Circuit], assigned during [Circuit.buildMatrix].
     */
    var index: Int = -1 // Assigned by Circuit
    var name = "vsource"

    /**
     * The potential of this VSource.
     *
     * This property is only a guess, updated by [stamp] and [change], assuming those are used correctly. It cannot be publicly assigned; use [stamp] and [change] instead. A typical intra-step invocation might be `vs.change(newPotential - vs.potential)`, but this is only safe to do within certain contexts and thus not implemented here by default.
     */
    var potential: Double = 0.0
        internal set

    override fun detail(): String {
        return "[$name ${this::class.simpleName}@${System.identityHashCode(this).toString(16)} ${current}A]"
    }

    override fun toString() = detail()

    /**
     * Stamp the voltage source into its owning circuit with absolute potential [v] (Volts) with a positive terminal [pos] and negative [neg] (both [Node]s in the same Circuit).
     *
     * This is only safe to do from within [Component.stamp], where it is known that the matrices have been initialized to zero. Otherwise, [change] should be used.
     *
     * This also assigns [potential] the value of [v].
     */
    fun stamp(pos: Node, neg: Node, v: Double) {
        circuit.stampVoltageSource(pos.index, neg.index, index, v)
        potential = v
    }

    /**
     * Stamp a voltage change for this voltage source by [dv] Volts (can be negative).
     *
     * This is only safe to do after [Component.stamp] (which is needed to set up the connectivity), but this generally includes, e.g., [Component.preStep], [Component.postStep], [Component.simStep], etc. . Since this only affects [Circuit.knowns], it is a relatively fast operation to generate a new solution.
     *
     * This also adds [dv] to [potential].
     */
    fun change(dv: Double) {
        if(index < 0) return
        circuit.stampVoltageChange(index, dv)
        potential += dv
    }
}
