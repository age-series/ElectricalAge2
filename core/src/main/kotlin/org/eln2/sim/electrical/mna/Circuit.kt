package org.eln2.sim.electrical.mna

import org.apache.commons.math3.linear.*
import org.eln2.debug.dprintln
import org.eln2.sim.IProcess
import org.eln2.sim.electrical.mna.component.*
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.IllegalArgumentException
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
    internal var vsources: List<WeakReference<VSource>> = emptyList()

    /* From Falstad: declare that a potential change dV in node b changes the current in node a by x*dV, complicated
       slightly by independent voltage sources. The unit of x is "Mhos", reciprocal Ohms, a unit of conductance.
     */
    fun stampMatrix(a: Int, b: Int, x: Double) {
        dprintln("C.sM $a $b $x")
        if(a < 0 || b < 0) return
        matrix!!.addToEntry(a, b, x)
        matrixChanged = true
    }
    
    fun stampKnown(i: Int, x: Double) {
        dprintln("C.sK $i $x")
        if(i < 0) return
        knowns!!.addToEntry(i, x)
        rightSideChanged = true
    }

    fun stampVoltageChange(i: Int, x: Double) {
        stampKnown(i + nodes.size, x)
    }

    fun stampResistor(a: Int, b: Int, r: Double) {
        dprintln("C.sR $a $b $r")
        val c = 1 / r
        if(!c.isFinite()) throw IllegalArgumentException("resistance $r is invalid")
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
        for(comp in comps) add(comp)
    }

    fun add(comp: Component): Component {
        components.add(comp)
        componentsChanged = true
        // This is the ONLY place where this should be set.
        comp.circuit = this
        for(i in 0 until comp.nodeCount) {
            val n = NodeRef(Node(this))
            compNodeMap.put(n, comp)
            comp.nodes.add(n)
        }
        for(i in 0 until comp.vsCount) {
            val vs = VSource(this)
            compVsMap.put(vs, comp)
            comp.vsources.add(vs)
        }
        comp.added()
        return comp
    }

    // Step 1: Whenever the number of components, or their nodal connectivity (not resistances, e.g.) changes, allocate
    // a matrix of appropriate size.
    protected fun buildMatrix() {
        dprintln("C.bM")
        val nodeSet: MutableSet<Node> = mutableSetOf()
        val vsourceSet: MutableSet<VSource> = mutableSetOf()

        components.forEach {
            nodeSet.addAll(it.nodes.map { it.node }.filter { it != ground.node })
            vsourceSet.addAll(it.vsources)
        }

        nodes = nodeSet.map { WeakReference(it) }.toList()
        vsources = vsourceSet.map { WeakReference(it) }.toList()

        for((i, n) in nodes.withIndex()) n.get()!!.index = i
        for((i, v) in vsources.withIndex()) v.get()!!.index = i

        dprintln("C.bM: n $nodes vs $vsources")
        
        // Null out the solver--it's definitely not valid anymore.
        solver = null

        // Acknowledge that changes have been dealt with
        componentsChanged = false
        connectivityChanged = false

        // Set other cascading changes so the solver runs for at least one iteration.
        matrixChanged = true
        rightSideChanged = true

        // Is there anything to do?
        if(nodes.isEmpty() || vsources.isEmpty()) {
            matrix = null
            return
        }

        val size = nodes.size + vsources.size
        dprintln("C.bM: size $size")
        matrix = MatrixUtils.createRealMatrix(size, size)
        knowns = ArrayRealVector(size)

        // Ask each component to contribute its steady state to the matrix
        dprintln("C.bM: stamp all $components")
        components.forEach { println("C.bM: stamp $it"); it.stamp() }
    }

    // Step 2: With the conductance and connectivity matrix populated, solve.
    private fun factorMatrix() {
        dprintln("C.fM")
        solver = if(matrix != null) LUDecomposition(matrix).solver else null
        matrixChanged = false
    }

    // Step 3: With known current and voltage sources, solve for unknowns (node potentials and source currents).
    private fun computeResult() {
        dprintln("C.cR")
        rightSideChanged = false
        if(solver == null) return
        try {
            val unknowns = solver!!.solve(knowns)

            // Copy data back out to the references for Component use
            for((i, n) in nodes.withIndex()) {
                n.get()!!.potential = unknowns.getEntry(i)
            }
            // Microoptimization: pull this member access into a local variable for this tight loop
            val sz = nodes.size
            for((i, v) in vsources.withIndex()) {
                v.get()!!.current = -unknowns.getEntry(i + sz)
            }
        } catch(e: SingularMatrixException) {
            dprintln("Singular: ${matrix}")
            if(matrix != null) print(MATRIX_FORMAT.format(matrix))
        }
    }

    fun step(dt: Double) {
        if(componentsChanged || connectivityChanged) {
            buildMatrix()
        }
        
        preProcess.keys.forEach { it.process(dt) }
        components.forEach { it.preStep(dt) }
        
        for(substep in 0 until maxSubSteps) {
            if(!(matrixChanged || rightSideChanged)) break  // Nothing to do

            val mc = matrixChanged  // Cache this because it gets overwritten by factorMatrix

            if (mc) {
                factorMatrix()
            }

            if (mc || rightSideChanged) {
                computeResult()
            }

            for(comp in components) comp.simStep()  // Allow non-linear components to request another substep
        }

        components.forEach { it.postStep(dt) }
        postProcess.keys.forEach { it.process(dt) }
    }

    override fun toString(): String {
        var ret= ""
        ret += components.map{ "$it" }
        ret += "\n"
        ret += nodes.map{ "$it" }
        ret += "\n"
        ret += vsources.map{ "$it" }
        ret += "\n"
        ret += matrix.toString()
        return ret
    }
}