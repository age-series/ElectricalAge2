package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.*

/**
 * The Component is an entity which can be simulated in a [Circuit]; generally speaking, these are little more than object-oriented interfaces to the state of the Circuit as expressed through its [Circuit.matrix], [Circuit.knowns], [Circuit.solver], and so forth. Much of the actual math implemented for a component depends on the "stamp" family of methods--see those for details.
 *
 * Components are implemented quite generally, borrowing from the flexibility of [Falstad's circuit simulator](https://www.falstad.com/circuit-java/); in theory, this implementation should be capable of everything that implementation is, including arbitrary nonlinear components (diodes, transistors, etc.), components with multiple nodes (two-port networks, e.g.), and more. The names and contexts are slightly changed, however (and this version has much less graphical cross-cutting code).
 *
 * The bare minimum required to implement a Component, as of this writing (but check for which fields and methods are abstract in your version):
 *
 * - [name]: A String used to identify the component for debugging.
 * - [nodeCount]: The number of [Node]s ("pins") this component will have.
 * - [stamp]: How this component initially contributes to the Circuit.
 *
 * In keeping with good practice, please try to keep this list of _requirements_ as small as possible. There are many more fields which have default implementations (including a great many methods which do nothing) but which are needed for specific functionality; if a default implementation makes sense, define one at this level.
 */
abstract class Component : IDetail {
    /**
     *  Ask this component to contribute (initial) values to the MNA matrices.
     *
     *  This is requested from [Circuit.buildMatrix]. It may not be called on every step (indeed, it shouldn't be), so this should only be relied upon to set the initial steady state. (Despite the name, the other Circuit "stamp" methods can be called at any time.)
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
     * How many nodes this component should have.
     *
     * `nodes` will be of this size only AFTER this component is added to a Circuit.
     */
    abstract val nodeCount: Int

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
    internal val isInCircuit: Boolean
        get() = circuit != null

    /**
     * Called when the Component has finished being added to a circuit--a good time for any extra registration steps.
     *
     * This is called after all the internal state--[nodes], [circuit], etc. have been initialized, and this is in [Circuit.components].
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
     */
    open fun preStep(dt: Double) {}

    /**
     * Called after substep iterations have finished in [Circuit.step].
     */
    open fun postStep(dt: Double) {}

    /** The list of [NodeRef]s held by this Component.
     *
     * Component implementations should index this list and avoid storing references anywhere else. The owning
    Circuit holds all Nodes it grants weakly, so taking other references will result in a memory leak.
     */
    var nodes: MutableList<NodeRef> = ArrayList(nodeCount)

    /**
     * The list of [VSource]s held by this Component.
     *
     * Like [nodes], these are only weakly held by the [Circuit].
     */
    var vsources: MutableList<VSource> = ArrayList(vsCount)

    /**
     * Get a [Node] by index (the one underlying the [NodeRef]).
     */
    inline fun node(i: Int) = nodes[i].node

    /** Connects two Components--makes nodes[nidx] the SAME Node as to.nodes[tidx], respecting precedence.

    This is ONLY safe to call AFTER the Component has been added to a Circuit.
     */
    fun connect(nidx: Int, to: Component, tidx: Int) {
        if (circuit == null) return
        val n = nodes[nidx]
        val tn = to.nodes[tidx]
        val (nnamed, tnnamed) = Pair(n.node.nameSet, tn.node.nameSet)
        if (nnamed && !tnnamed) tn.node.named(n.node.name)
        if (tnnamed && !nnamed) n.node.named(tn.node.name)
        if (n.node == tn.node) return  // Already connected
        tn.node.unite(n.node)
        nodes[nidx] = tn

        // Assertion intended--fail loudly if circuit mutated here.
        circuit!!.connectivityChanged = true
    }

    /** Sets a Node directly. This is generally only reasonable for circuit.ground.

    This is ONLY safe to call AFTER the Component has been added to a Circuit.
     */
    fun connect(nidx: Int, nr: NodeRef) {
        if (circuit == null) return
        val n = nodes[nidx].node
        val tn = nr.node
        val (nnamed, tnnamed) = Pair(n.nameSet, tn.nameSet)
        if (nnamed && !tnnamed) tn.named(n.name)
        if (tnnamed && !nnamed) n.named(tn.name)
        tn.unite(n)
        nodes[nidx].node = tn

        // Assertion intended--fail loudly if circuit mutated here.
        circuit!!.connectivityChanged = true
    }

    override fun toString(): String {
        return "${this::class.simpleName}@${System.identityHashCode(this).toString(16)} ${nodes.map { it.node.detail() }}"
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
    final override val nodeCount = 2

    // The orientation here is arbitrary, but helps keep signs consistent.
    /**
     * Get the positive [Node].
     *
     * By arbitrary convention, for compatibility with the [Falstad] circuit format, this is the second node.
     */
    open val pos: Node
        get() = node(1)

    /**
     * Get the negative [Node].
     *
     * This is arbitrarily the first Node, for [Falstad] compatibility.
     */
    open val neg: Node
        get() = node(0)

    /**
     * Get the potential across [pos] and [neg], signed positive when [pos] has a greater potential than [neg].
     */
    open val potential: Double
        get() = if (isInCircuit) pos.potential - neg.potential else 0.0
}
