package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.RootSystem
import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.misc.ISubSystemProcessFlush
import org.eln2.sim.mna.state.State
import java.util.*

class Line(root: RootSystem, private val resistors: LinkedList<Resistor>, private val states: LinkedList<State>) : Resistor(), ISubSystemProcessFlush, IAbstractor {

    init {
        val stateBefore = if (resistors.first.aPin === states.first) resistors.first.bPin else resistors.first.aPin
        val stateAfter = if (resistors.last.aPin === states.last) resistors.last.bPin else resistors.last.aPin
        recalculateR()
        root.queuedComponents.removeAll(resistors)
        root.queuedStates.removeAll(states)
        root.queuedComponents.add(this)
        connectTo(stateBefore, stateAfter)
        root.queuedProcessF.add(this)
        resistors.forEach {
            it.abstractedBy = this
            this.ofInterSystem = this.ofInterSystem or it.canBeReplacedByInterSystem()
        }
        states.forEach { it.abstractedBy = this }
    }

    override var subSystem: SubSystem? = null
        set(s) {
            s?.processF?.add(this)
            field = s
        }

    private var ofInterSystem = false

    fun add(c: Resistor) {
        ofInterSystem = ofInterSystem or c.canBeReplacedByInterSystem()
        resistors.add(c)
    }

    override fun canBeReplacedByInterSystem(): Boolean {
        return ofInterSystem
    }

    override fun quitSubSystem(s: SubSystem) {}

    override fun dirty(component: Component?) {
        recalculateR()
        if (isAbstracted()) abstractedBy!!.dirty(this)
    }

    override fun simProcessFlush() {
        val i = (aPin!!.state - bPin!!.state) * getRInv()
        var u = aPin!!.state
        val ir: Iterator<Resistor> = resistors.iterator()
        val `is`: Iterator<State> = states.iterator()
        while (`is`.hasNext()) {
            val s = `is`.next()
            val r = ir.next()
            u -= r.r * i
            s.state = u
            //u -= r.getR() * i;
        }
    }

    override fun returnToRootSystem(root: RootSystem) {
        for (r in resistors) {
            r.abstractedBy = null
        }
        for (s in states) {
            s.abstractedBy = null
        }
        restoreResistorIntoCircuit()
        root.queuedStates.addAll(states)
        root.queuedComponents.addAll(resistors)
        root.queuedProcessF.remove(this)
    }

    private fun restoreResistorIntoCircuit() {
        breakConnection()
    }

    private fun recalculateR() {
        var r = 0.0
        for (resistor in resistors) {
            r += resistor.r
        }
        this.r = r
    }

    override fun getAbstractorSubSystem(): SubSystem? {
        return subSystem
    }


}