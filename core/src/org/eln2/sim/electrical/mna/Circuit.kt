package org.eln2.sim.electrical.mna

import org.apache.commons.math3.linear.*
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
    private var matrix: RealMatrix? = null
    private var knowns: RealVector? = null
    private var solver: DecompositionSolver? = null
    private var nodes: List<WeakReference<Node>> = emptyList()
    private var vsources: List<WeakReference<VSource>> = emptyList()

    /* From Falstad: declare that a potential change dV in node b changes the current in node a by x*dV, complicated
       slightly by independent voltage sources. The unit of x is "Mhos", reciprocal Ohms, a unit of conductance.
     */
    fun stampMatrix(a: Int, b: Int, x: Double) {
        println("C.sM $a $b $x")
        if(a < 0 || b < 0) return
        matrix!!.addToEntry(a, b, x)
        matrixChanged = true
    }
    
    fun stampKnown(i: Int, x: Double) {
        println("C.sK $i $x")
        if(i < 0) return
        knowns!!.addToEntry(i, x)
        rightSideChanged = true
    }

    fun stampVoltageChange(i: Int, x: Double) {
        stampKnown(i + nodes.size, x)
    }

    fun stampResistor(a: Int, b: Int, r: Double) {
        println("C.sR $a $b $r")
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
        println("C.bM")
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

        println("C.bM: n $nodes vs $vsources")
        
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
        println("C.bM: size $size")
        matrix = MatrixUtils.createRealMatrix(size, size)
        knowns = ArrayRealVector(size)

        // Ask each component to contribute its steady state to the matrix
        println("C.bM: stamp all $components")
        components.forEach { println("C.bM: stamp $it"); it.stamp() }
    }

    // Step 2: With the conductance and connectivity matrix populated, solve.
    private fun factorMatrix() {
        println("C.fM")
        solver = if(matrix != null) LUDecomposition(matrix).solver else null
        matrixChanged = false
    }

    // Step 3: With known current and voltage sources, solve for unknowns (node potentials and source currents).
    private fun computeResult() {
        println("C.cR")
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
            println("Singular: ${matrix}")
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

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            main_1()
            println("---")
            main_2()
            println("---")
            main_3()
            println("---")
        }
        
        fun main_1() {
            println("main_1: === Basic circuit test ===")
            val c = Circuit()

            val vs = VoltageSource()
            val r1 = Resistor()

            c.add(vs)
            c.add(r1)

            vs.connect(1, r1, 0)
            vs.connect(0, r1, 1)
            vs.connect(1, c.ground)

            vs.u = 10.0
            r1.r = 100.0

            c.step(0.5)

            print("main_1: matrix:\n${MATRIX_FORMAT.format(c.matrix)}")

            for(comp in c.components) {
                println("main_1: comp $comp name ${comp.name} nodes ${comp.nodes} vs ${comp.vsources}")
                for(node in comp.nodes) {
                    println("\t node $node index ${node.node.index} ground ${node.node.isGround} potential ${node.node.potential}")
                }
            }

            for(node in c.nodes) {
                println("main_1: node ${node.get()} index ${node.get()?.index} potential ${node.get()?.potential}")
            }

            println("main_1: vs current: ${vs.i}\nmain_1: r1 current: ${r1.i}")
        }
        
        fun main_2() {
            println("main_2: === Capacitors and Inductors ===")
            val c = Circuit()

            val vs = VoltageSource()
            val c1 = Capacitor()
            val l1 = Inductor()
            val r1 = Resistor()
            val r2 = Resistor()

            c.add(vs, c1, l1, r1, r2)

            vs.connect(1, c.ground)
            vs.connect(0, r1, 0)
            vs.connect(0, r2, 0)
            r1.connect(1, c1, 0)
            r2.connect(1, l1, 0)
            c1.connect(1, c.ground)
            l1.connect(1, c.ground)

            vs.u = 10.0
            r1.r = 10.0
            r2.r = 10.0
            c1.c = 0.01
            l1.h = 1.0

            val fp = PrintStream(FileOutputStream("main_2.dat"))
            fp.println("#t\tc1.i\tl1.i\tr1.u\tr2.u\tvs.i")
            var t = 0.0
            val st = 0.05
            for(i in 0 until 25) {
                c.step(st)
                t += st
                println("main_2: t=$t c1.i=${c1.i} l1.i=${l1.i} r1(c1).u=${r1.u} r2(l1).u=${r2.u} vs.i=${vs.i}")
                fp.println("$t\t${c1.i}\t${l1.i}\t${r1.u}\t${r2.u}\t${vs.i}")
            }
            fp.close()
        }

        fun main_3() {
            println("main_3: === Diodes ===")
            val c = Circuit()

            val vs = VoltageSource()
            val r1 = Resistor()
            val r2 = Resistor()
            val r3 = Resistor()
            val d1 = RealisticDiode(DiodeData.default)
            val d2 = RealisticDiode(DiodeData.diodes["falstad-zener"] ?: error("no zener"))
            val d3 = IdealDiode()
            val diodes = arrayOf(d1, d2, d3)

            c.add(vs, r1, r2, r3, d1, d2, d3)
            
            vs.connect(1, c.ground)
            for(diode in diodes)
                vs.connect(0, diode, 0)
            for((r, d) in arrayOf(Pair(r1, d1), Pair(r2, d2), Pair(r3, d3))) {
                d.connect(1, r, 0)
                r.connect(1, c.ground)
                r.r = 10.0
            }

            val fp = PrintStream(FileOutputStream("main_3.dat"))
            fp.println("#t\tvs.u\tr1.i\tr1.p\tr2.1\tr2.p\tr3.i\tr3.p")
            var t = 0.0
            val st = 0.05
            for(i in -10 .. 10) {
                vs.u = i.toDouble()
                c.step(st)
                if(i == -10) println("main_3: matrix: ${MATRIX_FORMAT.format(c.matrix)}")
                t += st
                println("main_3: t=$t vs.u=${vs.u} r1.i=${r1.i} r1.p=${r1.p} r2.i=${r2.i} r2.p=${r2.p} r3.i=${r3.i} r3.p=${r3.p}")
                println("... knowns=${c.knowns}")
                fp.println("$t\t${vs.u}\t${r1.i}\t${r1.p}\t${r2.i}\t${r2.p}\t${r3.i}\t${r3.p}")
            }
        }
    }
}