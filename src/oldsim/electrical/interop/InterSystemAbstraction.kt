package org.eln2.oldsim.electrical.interop

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.RootSystem
import org.eln2.oldsim.electrical.mna.SubSystem
import org.eln2.oldsim.electrical.mna.component.*
import org.eln2.oldsim.electrical.mna.state.State
import org.eln2.oldsim.electrical.mna.state.VoltageState

class InterSystemAbstraction(private var root: RootSystem, private var interSystemResistor: Resistor) : IAbstractor, IDestructor {
    private var aNewState: VoltageState
    private var aNewResistor: Resistor
    private var aNewDelay: DelayInterSystem
    private var bNewState: VoltageState
    private var bNewResistor: Resistor
    private var bNewDelay: DelayInterSystem
    private var thevnaCalc: ThevnaCalculator

    private var aState: State? = null
    private var bState: State? = null
    private var aSystem: SubSystem? = null
    private var bSystem: SubSystem? = null

    init {
        aState = interSystemResistor.aPin
        bState = interSystemResistor.bPin
        aSystem = aState!!.subSystem?: throw Error()
        bSystem = bState!!.subSystem?: throw Error()
        aSystem!!.interSystemConnectivity.add(bSystem!!)
        bSystem!!.interSystemConnectivity.add(aSystem!!)
        aNewState = VoltageState()
        aNewResistor = Resistor()
        aNewDelay = DelayInterSystem()
        bNewState = VoltageState()
        bNewResistor = Resistor()
        bNewDelay = DelayInterSystem()
        aNewResistor.connectGhostTo(aState, aNewState)
        aNewDelay.connectTo(aNewState, null)
        bNewResistor.connectGhostTo(bState, bNewState)
        bNewDelay.connectTo(bNewState, null)
        calibrate()
        aSystem!!.addComponent(aNewResistor)
        aSystem!!.addState(aNewState)
        aSystem!!.addComponent(aNewDelay)
        bSystem!!.addComponent(bNewResistor)
        bSystem!!.addState(bNewState)
        bSystem!!.addComponent(bNewDelay)
        aSystem!!.breakDestructor.add(this)
        bSystem!!.breakDestructor.add(this)
        interSystemResistor.abstractedBy = this
        thevnaCalc = ThevnaCalculator(aNewDelay, bNewDelay)
        root.queuedProcessPre.add(thevnaCalc)
    }

    fun calibrate() {
        val u = (aState!!.state + bState!!.state) / 2
        aNewDelay.u = u
        bNewDelay.u = u
        val r = interSystemResistor.r / 2
        aNewResistor.r = r
        bNewResistor.r = r
    }

    override fun dirty(component: Component?) {
        calibrate()
    }

    override fun getAbstractorSubSystem(): SubSystem? {
        return aSystem
    }

    override fun destruct() {
        aSystem!!.breakDestructor.remove(this)
        aSystem!!.removeComponent(aNewDelay)
        aSystem!!.removeComponent(aNewResistor)
        aSystem!!.removeState(aNewState)
        bSystem!!.breakDestructor.remove(this)
        bSystem!!.removeComponent(bNewDelay)
        bSystem!!.removeComponent(bNewResistor)
        bSystem!!.removeState(bNewState)
        root.queuedProcessPre.remove(thevnaCalc)
        interSystemResistor.abstractedBy = null
        val aSys = aSystem
        aSys?.addComponent(interSystemResistor)
    }
}