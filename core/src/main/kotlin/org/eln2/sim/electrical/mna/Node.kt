package org.eln2.sim.electrical.mna

import org.eln2.debug.dprintln
import org.eln2.sim.electrical.mna.component.*
import org.eln2.space.Set

/**
 * A "node", in MNA; the non-resistive connections between [Component]s.
 *
 * Aside from identifying [Component]s' connections, the Nodes' potentials (relative to [Circuit.ground]) are computed as a result of the MNA algorithm.
 */
open class Node(var circuit: Circuit) : IDetail {
    /**
	 * The potential of this node, in Volts, relative to ground (as defined by the [Circuit]); an output of the simulation.
	 */
    open var potential: Double = 0.0
        internal set

    /**
	 * The index of this node into the [Circuit]'s matrices and vectors.
	 */
    open var index: Int = -1 // Assigned by Circuit
        internal set

    /**
	 * True if this node is ground (defined to be 0V).
	 */
    open val isGround = false

    /**
	 * A name for this node, set by [named] (with a default usually assigned by the class).
	 *
	 * This can be used for assigning a semantic meaning to a node, useful for debugging output. Programs should not rely on this value having any particular meaning other than its debug presentation.
	 */
    var name = "node"
        protected set

    private var nameSet = false

    /**
	 * Set the name of this Node, returning this.
	 *
	 * This is intended to be used at construction time, e.g. `Node(circuit).named("something")`. Usage afterward will provoke a warning when debugging is enabled.
	 */
    fun named(nm: String) = with(this) {
        if (nameSet) {
            dprintln("N.n: WARN: node already named $name being renamed to $nm")
        }
        name = nm
        nameSet = true
    }

    override fun detail(): String {
        return "[node val: $potential]"
    }

    /** Determine which node should prevail when two are merged in a Circuit.

	   This is mostly so subclasses of Node (if any) can maintain their existence when merged. The Node returning the
	   higher value is chosen; if both are equal (commonly the case), one is chosen arbitrarily.
	 */

    open fun mergePrecedence(other: Node): Int = 0

    fun stampResistor(to: Node, r: Double) {
        dprintln("N.sR $to $r")
        circuit.stampResistor(index, to.index, r)
    }
}

/**
 * A Node subclass for representing [Circuit.ground], with a higher [mergePrecedence], always [potential] 0V and [index] -1 (not assigned).
 *
 * The current recommended test for this special case is `node is GroundNode`.
 */
class GroundNode(circuit: Circuit) : Node(circuit) {
    override var potential: Double
        get() = 0.0
        set(value) {}

    override var index: Int
        get() = -1
        set(value) {}

    override val isGround = true

    override fun mergePrecedence(other: Node): Int = 100
}

/**
 * A "NodeRef", which simply refers to an underlying [Node].
 *
 * This additional level of indirection allows nodes between [Component]s to be united normally, _and_ for a single connection to [Circuit.ground] to connect _all_ connected Components to ground, even if the connections between Components were established earlier. In this way, it resembles a simpler version of [Set].
 */
data class NodeRef(var node: Node)
