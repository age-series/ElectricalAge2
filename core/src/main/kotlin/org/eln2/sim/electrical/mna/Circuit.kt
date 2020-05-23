package org.eln2.sim.electrical.mna

import org.apache.commons.math3.linear.*
import org.eln2.debug.dprint
import org.eln2.debug.dprintln
import org.eln2.sim.IProcess
import org.eln2.sim.electrical.mna.component.*
import java.lang.ref.WeakReference
import java.util.*
import org.eln2.parsers.falstad.*
import org.eln2.space.*

/**
 * The default format for debug matrix printouts from Circuits.
 */
val MATRIX_FORMAT = RealMatrixFormat("", "", "\t", "\n", "", "\t")

/**
 * The Circuit class: a representation of a nodal electrical circuit simulation.
 *
 * This is the primary entry point into the MNA solver; in order to simulate an electrical circuit, you will need an instance of this class.
 *
 * # Construction
 *
 * A Circuit is primarily configured through included [Component]s, which are [add]ed to the Circuit incrementally. Only _after_ such a Component is added does it get a proper sequence of [Node]s in its [Component.nodes] (as requested in its [Component.nodeCount]). These [NodeRef]s may be used to connect Components together once they are added to the simulation; generally, the order of Nodes is meaningful, and each Component subclass may define different meaning for the Nodes in this sequence. For example, [Port]s have a well-defined positive and negative terminal node.
 * 
 * Note that a Circuit should _only_ be made of a Connected Component (in the graph sense) of a circuit; having disjoint Connected Components in a Circuit will at least degrade performance considerably, and may cause the circuit to [become underconstrained][isFloating]--see below.
 *
 * ## Disconnections and Removal
 *
 * The current algorithm supports _only_ incremental addition and connection; removal is _not_ directly supported, as it has the potential to sever the connected component of the circuit. For this reason, online removal is delegated to a higher layer which can track the connected components (such as [Space]).
 *
 * The best way to simulate removal or disconnection is to rebuild the Circuit (or possibly Circuits, if disconnection occurred) without the components and connections to remove. It is safe to transfer ownership of a Component to a new Circuit, such that it retains its state, but note that it will be entirely disconnected.
 * 
 * # Simulation
 * 
 * Circuit state is advanced through [Circuit.step], which expects a timestep; for stability and performance reasons (especially with reactive and nonlinear components), it is best that this timestep be held constant.
 *
 * [Circuit.step] returns whether or not the simulation step was successful; if it was not successful, the output data of the Circuit should not be assumed to be in a sensible state. If the step was successful, the following outputs are made available:
 *
 * - Each [Node] receives its potential relative to the [ground] node;
 * - Each [VSource] receives its total current.
 *
 * These objects can be queried from the Components that hold them; the Circuit also retains a copy, but with no meaningful ordering.
 *
 * # Performance
 *
 * This class is highly optimized, with the goal of running ~50000 steps per second with a mean size of 20-30 nodes; to accomplish this, it makes heavy use of caching. When using (or designing) components, bear in mind the following performance characteristics:
 *
 * - Changing any independent voltage or current source is cheap; this adjusts only the input vector to the solved connectivity matrix.
 * - Changing connectivity, including resistance between nodes, is expensive, and requires the solver to be recomputed.
 *
 * In the current implementation, reactive components ([Capacitor], [Inductor]) change only current sources, and are thus fairly cheap. Nonlinear components (like [RealisticDiode]), however, may change resistances, and thus require multiple substeps to settle on a solution. The implementations of these Components have been chosen carefully to converge (often exponentially) to the correct value, but the [Circuit.maxSubSteps] tuning parameter can be adjusted to place a hard upper bound on the amount of time spent in substeps for nonlinear components.
 *
 * # Stability
 *
 * The core matrix of the MNA algorithm is mostly a connectivity matrix, and it must be invertible for a solution to exist. However, a number of marginal real-world circuits can cause issues with this assumption. Bear the following in mind when parameterizing inputs to the simulation:
 *
 * - The connectivity matrix has entries in terms of _conductance_ (reciprocal resistance), summed over all components. Infinite resistance is zero conductance, but 0 resistance is infinite conductance, and this _will_ cause the solver to fail. Perfect, non-resistive connections are best represented by connecting the underlying nodes directly--but see the note on disconnection above that this process is hard to undo.
 * - Along similar terms, numerical stability is best when resistances are within a few orders of magnitude of each other. This library is reliant upon Java's double (likely IEEE754 binary64) and all the burdens that come with it; very high resistances will result in very small conductances, and one should expect precision loss to occur if very large and small resistances are simultaneously connected to the same Node.
 * - The ground node is privileged as being _defined_ to zero potential. The exact choice of where to put this in the circuit doesn't matter, but it is traditionally placed on the node considered to have the lowest potential, often on the negative side of a DC "main" power supply. Failing to connect this node will result in a ["floating" Circuit][Circuit.isFloating] (see, e.g., [Falstad.floating]) which is likely underconstrained, causing the solver to fail.
 * - This point bears reiteration: the node voltages for a "connected circuit graph component" without any conductance to ground (or infinite resistance) is underconstrained, and will cause the solver to fail. This is mainly the reason you should rebuild a Circuit after disconnecting or removing Components.
 *
 * A successful [Circuit.step] does not guarantee that the values are valid; it is possible for the solver to return, e.g., NaN values. Any logic interfacing the Circuit should be prepared to receive invalid values gracefully.
 *
 * # Implementation Details
 *
 * The current version of this solver is heavily based on Falstad's [circuit simulator Java applet](https://www.falstad.com/circuit-java/).
 *
 * The linear algebra library is presently [Apache Commons Math](http://commons.apache.org/proper/commons-math/), but this is subject to change--and distinct Circuits need not use the same underlying implementation.
 *
 * The MNA algorithm itself is heavily borrowed from [Erik Cheever's Algorithmic MNA resource][MNA].
 *
 * [MNA]: https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA3.html
 */
class Circuit {

	/**
	 * Set when the conductivity [matrix] has changed.
	 *
	 * The matrix expresses connectivity and conductance, so this usually occurs if components are added or resistances change.
	 *
	 * Indicates that a [factorMatrix] call must occur, but the [Node] numbering has not changed (via [buildMatrix]).
	 */
	private var matrixChanged = false
	/**
	 * Set when the [knowns] vector has changed.
	 *
	 * This vector contains the known current and voltage source values.
	 *
	 * Indicates that [computeResult] must be called to retrieve up-to-date information.
	 */
	private var rightSideChanged = false
	/**
	 * Set when [Component]s have been added.
	 *
	 * This implies [buildMatrix] must be done, and that the [Node] numbering may have changed.
	 */
	private var componentsChanged = false
	/**
	 * Set when the connectivity of [Component]s changes.
	 *
	 * This includes calls to [Component.connect], and implies that [Node] renumbering may need to occur, as with [componentsChanged].
	 */
	// These ones below are called from Component methods
	internal var connectivityChanged = false

	/**
	 * The privileged ground node.
	 *
	 * This is defined to be zero potential with relation to every other node.
	 */
	// Don't ever add this to any node lists; its index is always invalid.
	var ground = NodeRef(GroundNode(this))

	/**
	 * The maximum number of substeps done to try to make non-linear [Component]s converge.
	 *
	 * Components _should_ be designed to converge exponentially anyway, but this covers pathological cases. The value can be considered an "acceptable slowdown factor" in the worst case.
	 */
	var maxSubSteps = 100
	/**
	 * A "small" value (like epsilon), used to determine if two Doubles are "close enough".
	 *
	 * Circuit does not use this directly, but some [Component]s use it to determine if they should stop iterating.
	 */
	var slack = 0.001
	/**
	 * Set if the last step was successful.
	 *
	 * This is exactly the value returned from [step]; it is false when [computeResult] fails to solve or write out its results.
	 */
	var success = true

	/**
	 * The [Component]s added to this Circuit.
	 *
	 * Order is preserved from addition, but otherwise insignificant.
	 *
	 * It should be an invariant that `this.components.all { it.circuit == this }`.
	 */
	val components = mutableListOf<Component>()
	/**
	 * A map from [NodeRef] to the owning [Component].
	 * 
	 * This does not include the [ground] NodeRef owned by the Circuit.
	 */
	val compNodeMap = WeakHashMap<NodeRef, Component>()

	/**
	 * A map from [VSource] to the owning [Component].
	 */
	// These don't merge, but keep this collection weak anyway so the size reflects component removal.
	val compVsMap = WeakHashMap<VSource, Component>()

	/**
	 * A list of closures to call before the Circuit step runs.
	 */
	val preProcess = WeakHashMap<IProcess, Unit>()
	/**
	 * A list of closures to call after the Circuit step finishes.
	 */
	val postProcess = WeakHashMap<IProcess, Unit>()

	// These fields are only for the solver--don't use them casually elsewhere
	/**
	 * The connectivity matrix.
	 *
	 * This matrix is square of size (nodes + vsources), with the upper-left square nodes-size matrix representing the conductances, and the two (nodes x vsources) rectangular matrices representing the connectivity of the voltage sources.
	 * 
	 * It is the "A Matrix" in the [MNA Algorithm][MNA].
	 *
	 * [MNA]: https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA3.html
	 */
	internal var matrix: RealMatrix? = null
	/**
	 * The "knowns" vector.
	 *
	 * This is a column vector of size (nodes + vsources); the first (nodes) elements are independent currents into the node (the sums of signed contributions of current sources), and the last (vsources) elements are the voltage source values.
	 *
	 * This is the "z matrix" in the [MNA Algorithm][MNA].
	 *
	 * [MNA]: https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA3.html
	 */
	internal var knowns: RealVector? = null
	/**
	 * A solver for [matrix], if it could be inverted.
	 *
	 * This is cached to quickly [computeResult] if there was no need to [buildMatrix] (and only [knowns] changed).
	 */
	internal var solver: DecompositionSolver? = null
	/**
	 * [Node]s owned by this Circuit.
	 * 
	 * They may be shared by any number of [Component]s.
	 *
	 * The order is significant; their position is their index into the rows and columns of [matrix] and [knowns]. This is also available via [Node.index] after a [buildMatrix].
	 */
	internal var nodes: List<WeakReference<Node>> = emptyList()
	/**
	 * [VSource]s owned by this Circuit.
	 *
	 * These are usually owned by some other [Component].
	 *
	 * The order is significant; their position is their index into the rows and columns of [matrix] and [knowns] (offset, as need be, by the size of [nodes]). This is available via [VSource.index] after a [buildMatrix].
	 */
	internal var voltageSources: List<WeakReference<VSource>> = emptyList()

	/**
	 * Add a value to a given row and column of [matrix].
	 *
	 * [a] and [b] should be indices of [Node]s; that is, they should be nonnegative and less than the length of [nodes]. Thus, these index into the "G submatrix" as expressed in the [MNA Algorithm][MNA]. Since this matrix should be symmetric, this method is usually invoked twice, the latter with [b] and [a] swapped.
	 *
	 * The interpretation, according to Falstad, is as follows: if the potential of [b] changes by `dV`, then the current into node [a] should change by [x] * `dV`. The unit of [x] is Siemens (reciprocal Ohms).
	 *
	 * This is the lowest level function that manipulates [matrix]; it is usually called via [stampResistor] and [stampVoltageSource]. These should only be called from [Component.stamp].
	 *
	 * [MNA]: https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA3.html
	 */
	/* From Falstad: declare that a potential change dV in node b changes the current in node a by x*dV, complicated
	   slightly by independent voltage sources. The unit of x is Siemens, reciprocal Ohms, a unit of conductance.
	 */
	fun stampMatrix(a: Int, b: Int, x: Double) {
		dprintln("C.sM $a $b $x")
		if (a < 0 || b < 0) return
		matrix!!.addToEntry(a, b, x)
		matrixChanged = true
	}

	/**
	 * Add a value to a given row of [knowns].
	 *
	 * If called with nonzero i less than the size of [nodes], change the independent current into node [i] by [x] Amperes. Otherwise, change the potential of [VSource] [i] - (size of [nodes]) by [x] Volts.
	 * 
	 * This is the lowest level function that manipulates [knowns]; it is usually called via [stampVoltageChange] and [stampCurrentSource]. These should only be called from [Component.stamp].
	 */
	fun stampKnown(i: Int, x: Double) {
		dprintln("C.sK $i $x")
		if (i < 0) return
		knowns!!.addToEntry(i, x)
		rightSideChanged = true
	}

	/**
	 * Change the potential of VSource [i] by [x] Volts.
	 *
	 * This calls [stampKnown] internally. It should usually be called from a method on a [Component] with a valid [VSource].
	 */
	fun stampVoltageChange(i: Int, x: Double) {
		stampKnown(i + nodes.size, x)
	}

	/**
	 * Add a resistor of [r] Ohms between nodes [a] and [b].
	 *
	 * This calls [stampMatrix] internally. It should only ever be called from [Component.stamp].
	 */
	fun stampResistor(a: Int, b: Int, r: Double) {
		dprintln("C.sR $a $b $r")
		val c = 1 / r
		if (!c.isFinite()) throw IllegalArgumentException("resistance $r is invalid")
		// Contribute positively to the on-diagonal elements
		stampMatrix(a, a, c)
		stampMatrix(b, b, c)
		// If both are non-ground, contribute negatively to the off-diagonal elements
		stampMatrix(a, b, -c)
		stampMatrix(b, a, -c)
	}

	/**
	 * Add the contribution of potential source [num] as [v] Volts with a positive terminal node [pos] and negative [neg].
	 *
	 * This calls [stampMatrix] and [stampKnown] internally. It should only ever be called from [Component.stamp].
	 */
	fun stampVoltageSource(pos: Int, neg: Int, num: Int, v: Double) {
		val vs = num + nodes.size
		stampMatrix(vs, neg, -1.0)
		stampMatrix(vs, pos, 1.0)
		stampMatrix(neg, vs, 1.0)
		stampMatrix(pos, vs, -1.0)
		stampKnown(vs, v)
	}

	/**
	 * Adds the contribution of a current source from [neg] into [pos] with [i] Amperes. (Current sources aren't indexed; this can be done at any time.)
	 *
	 * This calls [stampKnown] internally.
	 */
	fun stampCurrentSource(pos: Int, neg: Int, i: Double) {
		stampKnown(pos, -i)
		stampKnown(neg, i)
	}

	/**
	 * Add all of the [Component]s in the vararg list.
	 */
	fun add(vararg comps: Component) {
		for (comp in comps) add(comp)
	}

	/**
	 * Add a [Component] to the Circuit.
	 *
	 * This sets up the Component to be used (honoring [Component.nodeCount] and [Component.vsCount], setting [Component.circuit], etc.).
	 *
	 * Adding a component causes [componentsChanged] to become true; thus, [buildMatrix] is usually required before the next solve [step].
	 */
	fun add(comp: Component): Component {
		dprintln("C.a: $comp")
		if(comp.circuit != null) {  // Are we stealing this component?
			if(comp.circuit == this) return comp  // No need to do anything, it's already ours
			comp.circuit?.remove(comp)
		}

		components.add(comp)
		componentsChanged = true
		// This is the ONLY place where this should be set.
		comp.circuit = this
		for (i in 0 until comp.nodeCount) {
			val n = NodeRef(Node(this))
			compNodeMap.put(n, comp)
			comp.nodes.add(n)
		}
		for (i in 0 until comp.vsCount) {
			val vs = VSource(this)
			compVsMap.put(vs, comp)
			comp.vsources.add(vs)
		}
		comp.added()

		dprintln("C.a: $comp has nodes ${comp.nodes.map { it.node }} vs ${comp.vsources}")
		return comp
	}

	/**
	 * Rmove all of the [Component]s in the vararg list.
	 */
	
	fun remove(vararg comps: Component) {
		for(comp in comps) remove(comp)
	}

	/**
	 * Remove a [Component] from this Circuit.
	 *
	 * All connections to any [NodeRef] of this Component are lost. The [Component] itself is guaranteed to be in such a state that, if it were added again and reconnected, the simulation would continue as if the removal did not happen.
	 *
	 * If the removal succeeded, [componentsChanged] is set, and [buildMatrix] will run on the next [step].
	 */

	fun remove(comp: Component): Boolean {
		return if(components.remove(comp)) {
			comp.removed()
			comp.nodes.forEach { compNodeMap.remove(it) }
			comp.nodes.clear()
			comp.vsources.forEach { compVsMap.remove(it) }
			comp.vsources.clear()
			// This is the ONLY place where this should be cleared.
			comp.circuit = null
			componentsChanged = true
			true
		} else false
	}

	/**
	 * Determine if the Circuit is floating--having no finite-resistance connection to [ground].
	 *
	 * A floating circuit is likely to fail to [step], as it is underconstrained. Fixing this requires unifying any node with [ground]--any one will do, though it is traditionally the lowest-potential node of the Circuit.
	 */
	val isFloating: Boolean get() {
		if(componentsChanged || connectivityChanged) buildMatrix()
		return !components.any {
			it.nodes.any {
				it.node == ground.node
			}
		}
	}

	/**
	 * Build the [matrix] and the [nodes] and [voltageSources], imparting each with indices.
	 *
	 * This is somewhat expensive, but needs to happen when [componentsChanged] or [connectivityChanged], at most once per [step].
	 * 
	 * This does not need to happen when [matrixChanged], but [factorMatrix] does.
	 *
	 * These conditions are usually handled automatically for you, whenever [Component]s are [add]ed or [NodeRef]s are [Component.connect]ed.
	 */
	// Step 1: Whenever the number of components, or their nodal connectivity (not resistances, e.g.) changes, allocate
	// a matrix of appropriate size.
	protected fun buildMatrix() {
		dprintln("C.bM")
		val nodeSet: MutableSet<Node> = mutableSetOf()
		val voltageSourceSet: MutableSet<VSource> = mutableSetOf()

		components.forEach {
			dprintln("C.bM: component $it nodes ${it.nodes.map { it.node }}")
			nodeSet.addAll(it.nodes.map { it.node }.filter { it != ground.node })
			voltageSourceSet.addAll(it.vsources)
		}

		nodes = nodeSet.map { WeakReference(it) }.toList()
		voltageSources = voltageSourceSet.map { WeakReference(it) }.toList()

		for ((i, n) in nodes.withIndex()) n.get()!!.index = i
		for ((i, v) in voltageSources.withIndex()) v.get()!!.index = i

		dprintln("C.bM: n ${nodes.map { it.get()?.detail() }} vs ${voltageSources.map { it.get()?.detail() }}")

		// Acknowledge that changes have been dealt with
		componentsChanged = false
		connectivityChanged = false

		// Null out the solver--it's definitely not valid anymore.
		solver = null

		// Set other cascading changes so the solver runs for at least one iteration.
		matrixChanged = true
		rightSideChanged = true

		// Is there anything to do?
		if (nodes.isEmpty()) {
			matrix = null
			return
		}

		val size = nodes.size + voltageSources.size
		dprintln("C.bM: size $size")
		matrix = MatrixUtils.createRealMatrix(size, size)
		knowns = ArrayRealVector(size)

		// Ask each component to contribute its steady state to the matrix
		dprintln("C.bM: stamp all $components")
		components.forEach { dprintln("C.bM: stamp $it"); it.stamp() }
		dprintln("C.bM: final matrix:\n${MATRIX_FORMAT.format(matrix)}")
	}

	/**
	 * Factor the matrix, generating a [solver] for the [matrix].
	 *
	 * This has to happen whenever [matrixChanged]. This is definitely the case when the matrix was just built by [buildMatrix], but also can occur multiple times within a [step] if non-linear [Component]s are present (usually by adjusting resistances within their [Component.simStep]).
	 *
	 * This is somewhat expensive, but not as much as rebuilding the matrix.
	 */
	// Step 2: With the conductance and connectivity matrix populated, solve.
	private fun factorMatrix() {
		dprintln("C.fM")
		solver = if (matrix != null) LUDecomposition(matrix).solver else null
		matrixChanged = false
	}

	/**
	 * Compute the results of the MNA, solving for the unknowns (voltages of [Node]s and currents through [VSource]s).
	 *
	 * This has to happen whenever [rightSideChanged], which happens for non-linear and reactive components (or any variable current sources). As such, it can happen multiple times per [step].
	 *
	 * Assuming neither [factorMatrix] nor [buildMatrix] need to be called regularly, this is very cheap; for performance reasons, [Component]s should strive to only change [knowns] when possible.
	 *
	 * This routine can fail; [success] is set based on whether this method was successful.
	 */
	// Step 3: With known current and voltage sources, solve for unknowns (node potentials and source currents).
	private fun computeResult() {
		dprintln("C.cR")
		rightSideChanged = false
		success = false
		if (solver == null) return
		try {
			val unknowns = solver!!.solve(knowns)

			// Copy data back out to the references for Component use
			for ((i, n) in nodes.withIndex()) {
				n.get()!!.potential = unknowns.getEntry(i)
			}
			// Microoptimization: pull this member access into a local variable for this tight loop
			val sz = nodes.size
			for ((i, v) in voltageSources.withIndex()) {
				v.get()!!.current = -unknowns.getEntry(i + sz)
			}

			success = true
		} catch (e: SingularMatrixException) {
			dprintln("Singular: ${matrix}")
			if (matrix != null) dprint(MATRIX_FORMAT.format(matrix))
		}
	}

	/**
	 * Perform a simulation step.
	 *
	 * This calls [buildMatrix], [factorMatrix], and [computeResult] as many times as needed to converge on a stable solution (with substeps up to [maxSubSteps], if not sooner). Each of these methods is called lazily; the call is avoided if nothing relevant to the method has changed.
	 *
	 * The return value is the [success] field--whether or not [computeResult] was able to compute a solution. If this failed, the output data is likely meaningless; otherwise, the output data is stored in the fields of [nodes] and [voltageSources] to be consumed.
	 */
	fun step(dt: Double): Boolean {
		dprintln("C.s: dt=$dt")
		if (componentsChanged || connectivityChanged) {
			buildMatrix()
		}

		preProcess.keys.forEach { it.process(dt) }
		components.forEach { it.preStep(dt) }

		for (substep in 0 until maxSubSteps) {
			if (!(matrixChanged || rightSideChanged)) break  // Nothing to do

			if (matrixChanged) {
				factorMatrix()
				computeResult()
			} else if (rightSideChanged) {
				computeResult()
			}

			for (comp in components) comp.simStep()  // Allow non-linear components to request another substep
		}

		components.forEach { it.postStep(dt) }
		postProcess.keys.forEach { it.process(dt) }

		dprintln("C.s: success=$success")
		return success
	}

	override fun toString(): String {
		var ret = ""
		ret += components.map { "$it" }
		ret += "\n"
		ret += nodes.map { "$it" }
		ret += "\n"
		ret += voltageSources.map { "$it" }
		ret += "\n"
		ret += matrix.toString()
		return ret
	}

	/**
	 * Utility method to return a GraphViz source for this Circuit.
	 *
	 * This is useful to visualize a circuit. (I recommend using `neato` or `fdp`; the visualization is not that good in `dot` alone, despite the name, because `dot` expects more hierarchical graphs.)
	 */
	fun toDot(): String {
		val sb = StringBuilder()
		sb.append("graph {\n")
		// sb.append("\tgraph [imagepath=\"images\"];\n")
		sb.append("\tgraph [splines=ortho];\n")
		sb.append("\tnode [fontsize=8];\n")
		sb.append("\t// Nodes\n")
		sb.append("\t\"ground\" [shape=point label=\"ground\"];\n")
		nodes.forEach {
			sb.append("\t\"n${it.get()?.index}\" [shape=point label=\"n${it.get()?.index}\"];\n")
		}
		sb.append("\n\t// Components\n")
		components.forEach {
			sb.append("\t\"c${System.identityHashCode(it)}\" [label=\"${it.detail()}\"")
			val img = it.imageName
			if(img != null) sb.append(" image=\"images/$img.svg\" peripheries=0")
			sb.append("];\n")
		}
		sb.append("\n\t// Connections\n")
		components.forEach { cmp ->
			cmp.nodes.withIndex().forEach {
				val port = when(it.index) {
					0 -> ":w"
					1 -> ":e"
					else -> ""
				}
				sb.append("\t\"c${System.identityHashCode(cmp)}\"$port -- \"${if(it.value.node.isGround) "ground" else "n${it.value.node.index}"}\" [shape=box ")
				when(it.index) {
					0 -> sb.append("color=red")
					1 -> sb.append("color=blue")
					else -> sb.append("color=gray")
				}
				sb.append("];\n")
			}
		}
		sb.append("}\n")
		return sb.toString()
	}
}
