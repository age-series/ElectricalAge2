package org.eln2.sim.electrical.mna.component

import org.eln2.data.DisjointSet
import org.eln2.debug.dprintln
import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.GroundNode
import org.eln2.sim.electrical.mna.IDetail
import org.eln2.sim.electrical.mna.Node
import org.eln2.sim.electrical.mna.VSource

/**
* The Exception thrown when a [Component]'s [Circuit] is mutated during [Component.connect].
*
* This is generally not recoverable for this Component or Circuit, but it can be handled to isolate other Circuits.
*/
class ConnectionMutationException: Exception()

/**
 * A connectable "point" on a component. The [representative] of this [DisjointSet] owns a [Node].
 */
class Pin: DisjointSet() {
    private var internalNode: Node? = null
    var node: Node?
        get() = (representative as Pin).internalNode
        set(value) {
            dprintln("P.n.<set>: $this from=$node to=$value on=${representative as Pin}")
            (representative as Pin).internalNode = value
            dprintln("P.n.<set>: out $this node=$node")
        }

    override fun unite(other: DisjointSet) {
        val opin = other as? Pin
        if(opin != null) {  // This should be the hot path
            val node = Node.mergeData(internalNode, opin.internalNode)
            internalNode = node
            opin.internalNode = node
        }
        super.unite(other)
    }

    override fun toString(): String {
        return "Pin@${System.identityHashCode(this).toString(16)},rep@${System.identityHashCode(representative).toString(16)},node=$node"
    }
}

/**
 * The Component is an entity which can be simulated in a [Circuit]; generally speaking, these are little more than object-oriented interfaces to the state of the Circuit as expressed through its [Circuit.matrix], [Circuit.knowns], [Circuit.solver], and so forth. Much of the actual math implemented for a component depends on the "stamp" family of methods--see those for details.
 *
 * Components are implemented quite generally, borrowing from the flexibility of [Falstad's circuit simulator](https://www.falstad.com/circuit-java/); in theory, this implementation should be capable of everything that implementation is, including arbitrary nonlinear components (diodes, transistors, etc.), components with multiple nodes (two-port networks, e.g.), and more. The names and contexts are slightly changed, however (and this version has much less graphical cross-cutting code).
 *
 * The bare minimum required to implement a Component, as of this writing (but check for which fields and methods are abstract in your version):
 *
 * - [name]: A String used to identify the component for debugging.
 * - [pinCount]: The number of [Pin]s (equivalently, unique, disparate [Node]s) this component will have.
 * - [stamp]: How this component initially contributes to the Circuit.
 * - [detail]: Return a human-readable String describing this Component's state for debug purposes.
 *
 * In keeping with good practice, please try to keep this list of _requirements_ as small as possible. There are many more fields which have default implementations (including a great many methods which do nothing) but which are needed for specific functionality; if a default implementation makes sense, define one at this level.
 */
abstract class Component : IDetail {
    /**
     *  Ask this component to contribute (initial) values to the MNA matrices.
     *
     *  This is requested from [Circuit.buildMatrix]. It may not be called on every step (indeed, ideally it shouldn't be), so this should only be relied upon to set the initial steady state. (Despite the name, the other Circuit "stamp" methods can be called at any time.)
     *
     *  While the return value of [node] is a `[Node]?`, it is safe to non-null assert its value anytime after a Component has been [added] and [stamp]ed, up until it is [removed], as long as [stamp] is ONLY called from [Circuit]. Some buggy implementations might call [stamp] from elsewhere; you have been warned.
     */
    abstract fun stamp()

    /** Called by the simulator after each tentative solution substep. Components that are non-linear should override
    this method to change necessary MNA values if their values have not converged (using the Circuit "stamp" methods, usually). The simulator will detect this and
    simulate the next substep (up to a limit, [Circuit.maxSubSteps]).
     */
    open fun simStep() {}

    /** A name for this kind of Component, used for debugging. */
    abstract val name: String

    /** An image for this node in the images/ repository, without the .svg extension, used for debug drawing.
     */
    open val imageName: String? = null

    /**
     * How many pins this component should have.
     *
     * `pins` will be of this size only AFTER this component is added to a Circuit.
     */
    abstract val pinCount: Int

    /** How many potential sources are inside this component. There should be at least two nodes per source (although
    they can overlap). This contributes to a different part of the MNA parameters.

    Many Components don't have sources, while most have nodes, so this simply defaults to 0.
     */
    open val vsCount: Int = 0

    /**
     * Set when the component is added to the circuit.
     *
     * (Although the visibility doesn't do much in *this* project, this variable should be set from NOWHERE else.  - Grissess)
     */
    internal var circuit: Circuit? = null

    /**
     * True if this Component is in a [Circuit].
     *
     * If this is true, it is reasonable to assume that [pins] and [vsources] are filled out, but not safe to assume that [node] is non-null.
     */
    internal val isInCircuit: Boolean
        get() = circuit != null

    /**
     * Called when the Component has finished being added to a circuit--a good time for any extra registration steps.
     *
     * This is called after all the internal state--[pins], [circuit], etc. have been initialized, and this is in [Circuit.components]. Thus, it is safe to non-null assert [node] returns in this function.
     * 
     * While the [pins] are initialized, the [Node]s likely are not; accessing [node] directly is likely to return null.
     */
    open fun added() {}

    /**
     * Called when the Component has been removed--a good time for releasing resources.
     *
     * Note that the Component should *not* change state as a result of removal in order to guarantee that removal/addition/reconnection is idempotent.
     *
     * This is called before internal state is torn down (see [added]), but, as a caveat, this is no longer in [Circuit.components].
     */
    open fun removed() {}

    /**
     * Called before substep iterations start in [Circuit.step].
     *
     * It is always safe to non-null assert [node] returns in this function.
     */
    open fun preStep(dt: Double) {}

    /**
     * Called after substep iterations have finished in [Circuit.step].
     *
     * It is always safe to non-null assert [node] returns in this function.
     */
    open fun postStep(dt: Double) {}

    /** The list of [Pin]s held by this Component.
     *
     * Component implementations should index this list and avoid storing references anywhere else. The owning
    Circuit holds all Nodes it grants weakly, so taking other references will result in a memory leak.
     */
    @Suppress("LeakingThis") // Safe in a single threaded setting, apparently.
    var pins: MutableList<Pin> = ArrayList(pinCount)

    /**
     * The list of [VSource]s held by this Component.
     *
     * Like [pins], these are only weakly held by the [Circuit].
     */
    @Suppress("LeakingThis") // Safe in a single threaded setting, apparently.
    var vsources: MutableList<VSource> = ArrayList(vsCount)

    /**
     * Get a [Node] by [Pin] index, or null if it has not yet been assigned.
     *
     * The [Node]s are granted to [Pin]s by [Circuit.buildMatrix], so it is only safe to do so:
     *
     * - After this has been [added] to a [Circuit] with [Circuit.add], AND
     * - After [Node] indices have been assigned in [Circuit.buildMatrix].
     *
     * This is safe UNTIL the Component is [removed] with [Circuit.remove].
     *
     * Certain callbacks on this class are guaranteed to be called within this program state, such as [preStep], [postStep], [stamp], and possibly others, as long as [Circuit] is the sole invoker. Check the method documentation for details; if it does not explicitly state safety, you should gracefully handle null. In general, for these methods, you should take extra care not to call them unless this context is already guaranteed, or the method is known to guard against null nodes.
     *
     * Knowing this precise state's context is hard, since [Node]s can be reassigned at any time (which happens often for the [GroundNode]). It is safe to be defensive and check all nodes as needed.
     */
    fun node(i: Int) = pins[i].node

    /** Connects two Components--makes nodes[nidx].representative the SAME Node as to.nodes[tidx].representative, respecting precedence.

    This is ONLY safe to call AFTER the Component has been added to a [Circuit].
     */
    open fun connect(nidx: Int, to: Component, tidx: Int) {
        if (circuit == null) return
        pins[nidx].unite(to.pins[tidx])
        markConnectivityChanged()
    }

    /**
     * Ground the given [Pin].
     *
     * This is only safe to do AFTER the Component has been added to a [Circuit].
     */
    fun ground(nidx: Int) {
        if(circuit == null) return
        pins[nidx].node = circuit!!.ground
        markConnectivityChanged()
    }

    private fun markConnectivityChanged() {
        if(circuit == null) throw ConnectionMutationException()
        circuit!!.connectivityChanged = true
    }

    override fun toString(): String {
        return "${this::class.simpleName}@${System.identityHashCode(this).toString(16)} ${pins.map { it.node?.detail() }}"
    }
}

/**
 * A "Port" is a pair of [Node]s ("pins") satisfying the Port Condition: the current into one is equal to the current out of the other, and vice versa; equivalently, the sum of the currents is zero.
 *
 * Most "simple", ideal components satisfy this condition, and thus it is included here for utility. Components which do not satisfy this include some multi-Node Components (like transistors) where the relationship is more complicated, or Components which intentionally leak (or source) current somewhere; for those, subclass [Component] instead.
 *
 * The most important difference between implementing [Component] and [Port] is one less override: [nodeCount] is forced to be exactly two, as that is the definition of a Port. As of this writing, [name] and [stamp] are still needed.
 *
 * By virtue of upholding the Port Condition, the two involved Nodes are often given special names, a "positive" and "negative" terminal ([pos] and [neg]), across which the potential can be sensibly measured. By convention, the potential is positive when [pos]' potential is greater than [neg]. (If you know the resistance, like [Resistor]s do, the power dissipated can then be summarily computed.)
 */
abstract class Port : Component() {
    /**
     * A Port is, by definition, two nodes; thus, this cannot be overridden.
     */
    final override val pinCount = 2

    // The orientation here is arbitrary, but helps keep signs consistent.
    /**
     * Get the positive [Node].
     *
     * By arbitrary convention, for compatibility with the Falstad circuit format, this is the second node.
     */
    open val pos: Node?
        get() = node(1)

    /**
     * Get the negative [Node].
     *
     * This is arbitrarily the first Node, for Falstad compatibility.
     */
    open val neg: Node?
        get() = node(0)

    /**
     * Get the potential across [pos] and [neg], signed positive when [pos] has a greater potential than [neg].
     */
    open val potential: Double
        get() = (pos?.potential ?: 0.0) - (neg?.potential ?: 0.0)
}
