package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.*

abstract class Component : IDetail {
	/* Ask this component to contribute (initial) values to the MNA matrices. */
	abstract fun stamp()

	/* Called by the simulator after each tentative solution substep. Components that are non-linear should override
	   this method to change necessary MNA values if their values have not converged. The simulator will detect this and
	   simulate the next substep (up to a limit).
	 */
	open fun simStep() {}

	/* A name for this kind of Component, used for debugging. */
	abstract val name: String

	/* How many nodes this component should have. `nodes` will be of this size only AFTER this component is added to a
	   Circuit.
	 */
	abstract val nodeCount: Int

	/* How many potential sources are inside this component. There should be at least two nodes per source (although
	   they can overlap). This contributes to a different part of the MNA parameters.

	   Many Components don't have sources, while most have nodes, so this simply defaults to 0.
	 */
	open val vsCount: Int = 0

	/* Set when the component is added to the circuit. Although the visibility doesn't do much in *this* project, this
	   variable should be set from NOWHERE else.  - Grissess
	 */
	internal var circuit: Circuit? = null
	internal val isInCircuit: Boolean
		get() = circuit != null

	/* Called when the Component has finished being added to a circuit--a good time for any extra registration steps. */
	open fun added() {}

	/* Called before/after subiterations start. */
	open fun preStep(dt: Double) {}
	open fun postStep(dt: Double) {}

	/* Idem. Component implementations should index this list and avoid storing references anywhere else. The owning
	   Circuit holds all Nodes it grants weakly, so taking other references will result in a memory leak.
	 */
	var nodes: MutableList<NodeRef> = ArrayList(nodeCount)
	var vsources: MutableList<VSource> = ArrayList(vsCount)

	inline fun node(i: Int) = nodes[i].node

	/* Connects two Components--makes nodes[nidx] the SAME Node as to.nodes[tidx], respecting precedence.

	   This is ONLY safe to call AFTER the Component has been added to a Circuit.
	*/
	fun connect(nidx: Int, to: Component, tidx: Int) {
		if (circuit == null) return
		val n = nodes[nidx]
		val tn = to.nodes[tidx]
		if (n.node == tn.node) return  // Already connected
		if (tn.node.mergePrecedence(n.node) > n.node.mergePrecedence(tn.node)) {
			nodes[nidx] = tn
		} else {
			to.nodes[tidx] = n
		}
		// Assertion intended--fail loudly if circuit mutated here.
		circuit!!.connectivityChanged = true
	}

	/* Sets a Node directly. This is generally only reasonable for circuit.ground.

	   This is ONLY safe to call AFTER the Component has been added to a Circuit.
	 */
	fun connect(nidx: Int, nr: NodeRef) {
		if (circuit == null) return
		val n = nodes[nidx].node
		val tn = nr.node
		if (tn.mergePrecedence(n) > n.mergePrecedence(tn)) {
			nodes[nidx].node = nr.node
		} else {
			nr.node = nodes[nidx].node
		}
		circuit!!.connectivityChanged = true
	}

	override fun toString(): String {
		return "${this::class.java.simpleName} ${nodes.map { "${it.node.detail()}" }}"
	}
}

abstract class Port : Component() {
	override val nodeCount = 2

	// The orientation here is arbitrary, but helps keep signs consistent.
	open val pos: Node
		get() = node(0)
	open val neg: Node
		get() = node(1)

	open val u: Double
		get() = if (isInCircuit) pos.potential - neg.potential else 0.0
}
