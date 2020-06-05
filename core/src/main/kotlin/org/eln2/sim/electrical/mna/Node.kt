package org.eln2.sim.electrical.mna

import org.eln2.debug.dprintln
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.data.DisjointSet

/**
 * A "node", in MNA; the non-resistive connections between [Component]s. All [Pin]s which are connected share a single instance of Node.
 *
 * Aside from identifying [Component]s' connections, the Nodes' potentials (relative to [Circuit.ground]) are computed as a result of the MNA algorithm.
 */
open class Node(var circuit: Circuit) : IDetail, DisjointSet() {
    companion object {
        /**
         * Determine which of two (nullable) Nodes should subsume the other when the two are about to be merged.
         *
         * This respects [priority], and tries to return the distinguished one, if any, which is not null.
         *
         * It also tries to propagate [name] as best it can.
         */
        fun mergeData(a: Node?, b: Node?): Node? {
            val res = when(Pair(a == null, b == null)) {
                Pair(true, true) -> null
                Pair(true, false) -> b
                Pair(false, true) -> a
                Pair(false, false) -> {
                    // Forgive me some non-null assertions; the compiler cannot see that these variables will not be
                    // null in this branch by very virtue of the test, but we know it to be true :)
                    val (higher, lower) = if(a!!.priority > b!!.priority) Pair(a, b) else Pair(b, a)
                    if(lower.nameSet) higher.name = lower.name
                    higher
                }
                else -> error("unreachable")  // TODO: Any better function for this?
            }
            dprintln("$a, $b => $res")
            return res
        }
    }
    /**
     * The potential of this node, in Volts, relative to ground (as defined by the [Circuit]); an output of the simulation.
     */
    open var potential: Double = 0.0
        internal set

    /**
     * The index of this node into the [Circuit]'s matrices and vectors.
     */
    open var index: Int = -1  // Assigned by Circuit
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

    /**
     * A boolean that determines if this node has been named yet.
     */
    var nameSet = false
        protected set

    /**
     * Set the name of this Node, returning this.
     *
     * This is intended to be used at construction time, e.g. `Node(circuit).named("something")`. Usage afterward will provoke a warning when debugging is enabled.
     */
    fun named(nm: String) = with(this) {
        if (nameSet && name != nm) {
            dprintln("WARN: node already named $name being renamed to $nm")
        }
        name = nm
        nameSet = true
    }

    override fun detail(): String {
        return "[$name ${this::class.simpleName}@${System.identityHashCode(this).toString(16)} ${potential}V @$index]"
    }

    override fun toString() = detail()

    fun stampResistor(to: Node, r: Double) {
        dprintln("$this $to $r")
        circuit.stampResistor(index, to.index, r)
    }

}

/**
 * A Node subclass for representing [Circuit.ground], with a higher mergePrecedence, always [potential] 0V and [index] -1 (not assigned).
 *
 * The current recommended test for this special case is `node.isGround`.
 */
class GroundNode(circuit: Circuit) : Node(circuit) {
    override var potential: Double
        get() = 0.0
        set(_) {}

    override var index: Int
        get() = -1
        set(_) {}

    override val isGround = true

    override val priority: Int = 100
}
