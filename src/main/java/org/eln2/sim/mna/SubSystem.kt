package org.eln2.sim.mna

import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.QRDecomposition
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import org.eln2.sim.mna.component.Component
import org.eln2.sim.mna.component.Delay
import org.eln2.sim.mna.component.Resistor
import org.eln2.sim.mna.component.VoltageSource
import org.eln2.sim.mna.misc.IDestructor
import org.eln2.sim.mna.misc.ISubSystemProcessFlush
import org.eln2.sim.mna.misc.ISubSystemProcessI
import org.eln2.sim.mna.state.State
import org.eln2.sim.mna.state.VoltageState
import java.util.*

class SubSystem(val rootSystem: RootSystem?, val dt: Double) {
    private val _components = mutableListOf<Component>()
    private val _states = mutableListOf<State>()

    fun getComponents(): List<Component> = _components
    fun getStates(): List<State> = _states

    val breakDestructor = LinkedList<IDestructor>()
    val interSystemConnectivity = mutableListOf<SubSystem>()
    val processI = mutableListOf<ISubSystemProcessI>()
    val processF = mutableListOf<ISubSystemProcessFlush>()

    var matrixValid = false

    var stateCount = 0

    private var _a: RealMatrix = MatrixUtils.createRealMatrix(1, 1)

    private var singularMatrix = false

    private var _aInvData: Array<DoubleArray>? = null

    private var _iData: DoubleArray = DoubleArray(0)
    private var _xTempData: DoubleArray = DoubleArray(0)

    private var breaked: Boolean = false

    fun addComponent(c: Component) {
        _components.add(c)
        c.subSystem = this
        matrixValid = false
    }

    fun addState(s: State) {
        _states.add(s)
        s.addedTo(this)
        matrixValid = false
    }

    fun removeComponent(c: Component) {
        _components.remove(c)
        c.quitSubSystem(this)
        matrixValid = false
    }

    fun removeState(s: State) {
        _states.remove(s)
        s.quitSubSystem()
        matrixValid = false
    }

    private fun generateMatrix() {
        val stateCount = _states.size
        _a = MatrixUtils.createRealMatrix(stateCount, stateCount)
        _iData = DoubleArray(stateCount)
        _xTempData = DoubleArray(stateCount)
        var id = 0
        for (s in _states) {
            s.id = id++
        }
        _components.forEach { it.applyTo(this) }

        //	org.apache.commons.math3.linear.
        try { //FieldLUDecomposition QRDecomposition  LUDecomposition RRQRDecomposition
            printMatrix(_a)
            val aInv = QRDecomposition(_a).solver.inverse
            _aInvData = aInv.data
            printMatrix(aInv)
            singularMatrix = false
        } catch (e: Exception) {
            singularMatrix = true
            if (stateCount > 1) {
                println("//////////SingularMatrix////////////")
            }
        }
        matrixValid = true
    }

    fun addToA(a: State?, b: State?, v: Double) {
        if (a == null || b == null) return
        _a.addToEntry(a.id, b.id, v)
    }

    fun addToI(s: State?, v: Double) {
        if (s == null) return
        _iData[s.id] = v
    }

    fun step() {
        stepCalc()
        stepFlush()
    }

    fun stepCalc() {
        if (!matrixValid) generateMatrix()
        if (singularMatrix) return

        for (y in 0 until stateCount) {
            _iData[y] = 0.0
        }

        processI.forEach { it.simProcessI(this) }

        for (idx2 in 0 until stateCount) {
            var stack = 0.0
            for (idx in 0 until stateCount) {
                stack += _aInvData!![idx2][idx] * _iData[idx]
            }
            _xTempData[idx2] = stack
        }


    }

    fun stepFlush() {
        if (!singularMatrix) {
            for (idx in 0 until stateCount) {
                _states[idx].state = _xTempData[idx]
            }
        } else {
            _states.forEach { it.state = 0.0 }
        }
        processF.forEach { it.simProcessFlush() }
    }

    @Suppress("unused")
    fun solve() {
        if (!matrixValid) generateMatrix()
        if (singularMatrix) return

        for (y in 0 until stateCount) {
            _iData[y] = 0.0
        }
        processI.forEach { it.simProcessI(this) }
        for (idx2 in 0 until stateCount) {
            var stack = 0.0
            for (idx in 0 until stateCount) {
                stack += _aInvData!![idx2][idx] * _iData[idx]
            }
            _xTempData[idx2] = stack
        }
    }

    fun solve(pin: State): Double {
        if (!matrixValid) {
            generateMatrix()
        }

        if (singularMatrix) return 0.0

        for (y in 0 until stateCount) {
            _iData[y] = 0.0
        }
        for (p in processI) {
            p.simProcessI(this)
        }
        val idx2 = pin.id
        var stack = 0.0
        for (idx in 0 until stateCount) {
            stack += _aInvData!![idx2][idx] * _iData[idx]
        }
        return stack
    }

    fun breakSystem(): Boolean {
        if (breaked) return false
        while (!breakDestructor.isEmpty()) {
            breakDestructor.pop().destruct()
        }
        _components.forEach {
            it.quitSubSystem(this)
            if (rootSystem != null)
                it.returnToRootSystem(rootSystem)
        }
        _states.forEach {
            it.quitSubSystem()
            if (rootSystem != null)
                it.returnToRootSystem(rootSystem)
        }

        rootSystem?.systems?.remove(this)

        matrixValid = false

        breaked = true
        return true
    }

    override fun toString(): String {
        return _components.joinToString { it.toString() }
    }

    fun componentSize(): Int {
        return _components.size
    }

    companion object {
        @JvmStatic
        fun main(arg: Array<String>) {
            val s = SubSystem(null, 0.1)
            val n1 = VoltageState()
            val n2 = VoltageState()
            val n3 = VoltageState()
            val u1 = VoltageSource()
            val r1 = Resistor()
            val r2 = Resistor()
            val d1 = Delay()

            s.addState(n1)
            s.addState(n2)
            s.addState(n3)

            u1.u = 11.0
            u1.connectTo(n1, null)

            s.addComponent(u1)

            r1.r = 5.0
            r1.connectTo(n1, n2)

            d1.set(12.0)
            d1.connectTo(n2, n3)

            r2.r = 15.0
            r2.connectTo(n3, null)

            s.addComponent(r1)
            s.addComponent(d1)
            s.addComponent(r2)

            for (x in 0 until 100) {
                s.step()
            }

            println(s)

            s._states.forEach {
                println(it)
                println(it.javaClass.simpleName)
                if (it !in listOf<State>(n1, n2, n3)) {
                    println("this is the state?")
                    println(it.getNotSimulated())
                    it.components.forEach{ it2 -> println(it2)}
                }
            }

            println("END")

            s.step()
            s.step()
            s.step()
        }

        fun printMatrix(m: RealMatrix) {
            println((0..30).joinToString("") { "=" })
            m.data.forEach {
                print("[")

                for (x in it) {
                    val str = "%.3f".format(x)
                    val num = str.length
                    var out = ""
                    if (num < 8) {
                        for (z in 0 until (8 - num)) {
                            out += " "
                        }
                    }
                    out += "$str,"
                    print(out)
                }

                println("]")
            }
            val svd = SingularValueDecomposition(m)
            // Broken or large numbers are bad. Inverses are typically pretty ill-conditioned, but we're looking for egregious ones.
            // For every order of magnitude from 10^n, we get n more digits of error (apparently).
            // Some people say 10e8 or 10e12 may be more realistic? Not sure I want that much error. I set 10e4 for now.
            // Doubles have (roughly?) 15 decimal digits of precision. I can see 4 of them go away without too much trouble.
            println("Condition of Matrix: ${"%.3f".format(svd.conditionNumber)}")
            println((0..30).joinToString("") { "=" })
        }
    }
}