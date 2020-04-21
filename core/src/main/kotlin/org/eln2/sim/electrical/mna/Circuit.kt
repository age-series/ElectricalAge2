package org.eln2.sim.electrical.mna

import org.apache.commons.math3.linear.*
import org.eln2.debug.dprint
import org.eln2.debug.dprintln
import org.eln2.sim.IProcess
import org.eln2.sim.electrical.mna.component.Component
import java.lang.ref.WeakReference
import java.util.*

val MATRIX_FORMAT = RealMatrixFormat("", "", "\t", "\n", "", "\t")

class Circuit {

	private var matrixChanged = false
	private var rightSideChanged = false
	private var componentsChanged = false

	// These ones below are called from Component methods
	internal var connectivityChanged = false

	// Don't ever add this to any node lists; its index is always invalid.
	var ground = NodeRef(GroundNode(this))

	var maxSubSteps = 100
	var slack = 0.001
	var success = true

	val components = mutableListOf<Component>()
	val compNodeMap = WeakHashMap<NodeRef, Component>()

	// These don't merge, but keep this collection weak anyway so the size reflects component removal.
	val compVsMap = WeakHashMap<VSource, Component>()

	val preProcess = WeakHashMap<IProcess, Unit>()
	val postProcess = WeakHashMap<IProcess, Unit>()

	// These fields are only for the solver--don't use them casually elsewhere
	internal var matrix: RealMatrix? = null
	internal var knowns: RealVector? = null
	internal var solver: DecompositionSolver? = null
	internal var nodes: List<WeakReference<Node>> = emptyList()
	internal var voltageSources: List<WeakReference<VSource>> = emptyList()

	/* From Falstad: declare that a potential change dV in node b changes the current in node a by x*dV, complicated
	   slightly by independent voltage sources. The unit of x is Siemens, reciprocal Ohms, a unit of conductance.
	 */
	fun stampMatrix(a: Int, b: Int, x: Double) {
		dprintln("C.sM $a $b $x")
		if (a < 0 || b < 0) return
		matrix!!.addToEntry(a, b, x)
		matrixChanged = true
	}

	fun stampKnown(i: Int, x: Double) {
		dprintln("C.sK $i $x")
		if (i < 0) return
		knowns!!.addToEntry(i, x)
		rightSideChanged = true
	}

	fun stampVoltageChange(i: Int, x: Double) {
		stampKnown(i + nodes.size, x)
	}

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

	fun stampVoltageSource(pos: Int, neg: Int, num: Int, v: Double) {
		val vs = num + nodes.size
		stampMatrix(vs, neg, -1.0)
		stampMatrix(vs, pos, 1.0)
		stampMatrix(neg, vs, 1.0)
		stampMatrix(pos, vs, -1.0)
		stampKnown(vs, v)
	}

	fun stampCurrentSource(pos: Int, neg: Int, i: Double) {
		stampKnown(pos, -i)
		stampKnown(neg, i)
	}

	fun add(vararg comps: Component) {
		for (comp in comps) add(comp)
	}

	fun add(comp: Component): Component {
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
		return comp
	}

	val isFloating: Boolean get() {
		if(componentsChanged || connectivityChanged) buildMatrix()
		return !components.any {
			it.nodes.any {
				it.node == ground.node
			}
		}
	}

	// Step 1: Whenever the number of components, or their nodal connectivity (not resistances, e.g.) changes, allocate
	// a matrix of appropriate size.
	protected fun buildMatrix() {
		dprintln("C.bM")
		val nodeSet: MutableSet<Node> = mutableSetOf()
		val voltageSourceSet: MutableSet<VSource> = mutableSetOf()

		components.forEach {
			nodeSet.addAll(it.nodes.map { it.node }.filter { it != ground.node })
			voltageSourceSet.addAll(it.vsources)
		}

		nodes = nodeSet.map { WeakReference(it) }.toList()
		voltageSources = voltageSourceSet.map { WeakReference(it) }.toList()

		for ((i, n) in nodes.withIndex()) n.get()!!.index = i
		for ((i, v) in voltageSources.withIndex()) v.get()!!.index = i

		dprintln("C.bM: n $nodes vs $voltageSources")

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
	}

	// Step 2: With the conductance and connectivity matrix populated, solve.
	private fun factorMatrix() {
		dprintln("C.fM")
		solver = if (matrix != null) LUDecomposition(matrix).solver else null
		matrixChanged = false
	}

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

	fun step(dt: Double): Boolean {
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
