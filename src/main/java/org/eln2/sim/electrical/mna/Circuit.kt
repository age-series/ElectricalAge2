package org.eln2.sim.electrical.mna

import org.eln2.sim.IProcess
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.electrical.mna.component.VoltageSource

class Circuit {

    private var matrixChanged: Boolean = false
    private var rightSideChanged: Boolean = false

    val nodes = mutableListOf<Node>()
    val components = mutableListOf<Component>()

    val preProcess = mutableListOf<IProcess>()
    val postProcess = mutableListOf<IProcess>()

    private fun prepareMatrix() {
        // get a list of nodes from the components themselves. Iterate across them or whatever?

        // build the matrix of that size. Try to be generic enough that it's simple to load into either of the Matrix libraries or perhaps make some easy way to load into them

        // this will call stamp if the components need them called. The class decides?
        components.forEach { it.update(this) }
    }

    private fun factorMatrixA() {
        // do the factorization
        matrixChanged = false
    }

    // The function may change depending on how we need these results to be put whereever... the solve(pin) is what was in Eln.
    private fun computeResult() {
        // compute the other stuff. Faster than factoring. :)
        rightSideChanged = false
    }

    fun step(dt: Double) {
        preProcess.forEach { it.process(dt) }
        if (matrixChanged) {
            prepareMatrix()
            factorMatrixA()
        }
        if (matrixChanged or rightSideChanged) {
            computeResult()
        }
        postProcess.forEach { it.process(dt) }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            //todo: some test here
            val c = Circuit()

            val vs = VoltageSource()
            val n1 = Node()
            val r1 = Resistor()

            vs.nodes[0] = null
            vs.nodes[1] = n1
            vs.u = 10.0

            r1.nodes[0] = n1
            r1.nodes[1] = null
            r1.r = 100.0

            c.step(0.5)

            //print(r1.getI())
        }
    }
}