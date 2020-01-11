package org.eln2.oldsim.electrical

import org.eln2.oldsim.electrical.interop.InterSystem
import org.eln2.oldsim.electrical.interop.InterSystemAbstraction
import org.eln2.oldsim.electrical.mna.SubSystem
import org.eln2.oldsim.electrical.mna.component.*
import org.eln2.oldsim.electrical.interop.IRootSystemPreStepProcess
import org.eln2.oldsim.electrical.mna.process.ISubSystemProcessFlush
import org.eln2.oldsim.electrical.mna.state.State
import org.eln2.oldsim.electrical.mna.state.VoltageState
import java.util.*

class RootSystem(val dt: Double, val interSystemOverSampling: Int) {

    val systems = mutableListOf<SubSystem>()

    val queuedComponents = mutableListOf<Component>()
    val queuedStates = mutableListOf<State>()
    val queuedProcessF = mutableListOf<ISubSystemProcessFlush>()
    val queuedProcessPre = mutableListOf<IRootSystemPreStepProcess>()

    fun generate() {
        if (!queuedComponents.isEmpty() || !queuedStates.isEmpty()) {
            generateLine()
            generateSystems()
            generateInterSystems()
            var stateCnt = 0
            var componentCnt = 0
            systems.forEach {
                stateCnt += it.getStates().size
                componentCnt += it.getComponents().size
            }
        }
    }

    private fun isValidForLine(s: State): Boolean {
        if (!s.canBeSimplifiedByLine) return false
        val sc: List<Component> = s.getComponentsNotAbstracted()?: return false
        if (sc.size != 2) return false
        sc.filter{it !is Resistor}.forEach { return false }
        return true
    }

    private fun generateLine() {
        val stateScope: MutableSet<State> = HashSet()
        //HashSet<Resistor> resistorScope = new HashSet<Resistor>();
        queuedStates.filter{ isValidForLine(it)}.forEach { stateScope.add(it) }
        while (!stateScope.isEmpty()) {
            val sRoot = stateScope.iterator().next()
            var sPtr = sRoot
            var rPtr = sPtr.getComponentsNotAbstracted()!![0] as Resistor
            while (true) {
                for (c in sPtr.getComponentsNotAbstracted()!!) {
                    if (c !== rPtr) {
                        rPtr = c as Resistor
                        break
                    }
                }
                var sNext: State? = null
                if (sPtr !== rPtr.aPin) sNext = rPtr.aPin else if (sPtr !== rPtr.bPin) sNext = rPtr.bPin
                if (sNext == null || sNext === sRoot || !stateScope.contains(sNext)) break
                sPtr = sNext
            }
            val lineStates = LinkedList<State>()
            val lineResistors = LinkedList<Resistor>()
            lineResistors.add(rPtr)
            //rPtr.lineReversDir = rPtr.aPin == sPtr;
            while (true) {
                lineStates.add(sPtr)
                stateScope.remove(sPtr)
                for (c in sPtr.getComponentsNotAbstracted()!!) {
                    if (c !== rPtr) {
                        rPtr = c as Resistor
                        break
                    }
                }
                lineResistors.add(rPtr)
                //rPtr.lineReversDir = sPtr == rPtr.bPin;
                var sNext: State? = null
                if (sPtr !== rPtr.aPin) sNext = rPtr.aPin else if (sPtr !== rPtr.bPin) sNext = rPtr.bPin
                if (sNext == null || !stateScope.contains(sNext)) break
                sPtr = sNext
            }
            if (lineResistors.first === lineResistors.last) {
                lineResistors.pop()
                lineStates.pop()
            }
            //stateScope.removeAll(lineStates);
            Line(this, lineResistors, lineStates)
        }
    }

    /*	private void generateBreak() {
		for (Component c : (HashSet<Component>) addComponents.clone()) {
			for (State s : c.getConnectedStates()) {
				if (s == null) continue;
				if (s.getSubSystem() != null) {
					breakSystem(s.getSubSystem());
				}
				if (s.isAbstracted()) {
					s.abstractedBy.breakAbstraction(this);
				}
			}
		}
	}*/
    private fun generateSystems() {
        println(queuedStates)
        queuedStates.filter{it.mustBeFarFromInterSystem}.filter {it.subSystem == null}.forEach { buildSubSystem(it) }
        println(queuedStates)
        queuedStates.forEach {
            println(it)
            buildSubSystem(it)
        }
    }

    fun generateInterSystems() {
        val ic: MutableIterator<Component> = queuedComponents.iterator()
        while (ic.hasNext()) {
            val r = ic.next() as Resistor
            // If a pin is disconnected, we can't be intersystem
            if (r.aPin == null || r.bPin == null) continue
            InterSystemAbstraction(this, r)
            ic.remove()
        }
    }

    fun step() {
        generate()
        for (idx in 0 until interSystemOverSampling) {
            queuedProcessPre.forEach { it.rootSystemPreStepProcess() }
        }
        systems.forEach { it.stepCalc() }
        systems.forEach { it.stepFlush() }
        queuedProcessF.forEach { it.simProcessFlush() }
    }

    private fun buildSubSystem(root: State) {
        val componentSet: MutableSet<Component> = HashSet()
        val stateSet: MutableSet<State> = HashSet()
        val roots = LinkedList<State>()
        roots.push(root)
        buildSubSystem(roots, componentSet, stateSet)
        queuedComponents.removeAll(componentSet)
        queuedStates.removeAll(stateSet)
        val subSystem = SubSystem(this, dt)
        stateSet.forEach { subSystem.addState(it) }
        componentSet.forEach { subSystem.addComponent(it) }
        systems.add(subSystem)
    }

    private fun buildSubSystem(roots: LinkedList<State>, componentSet: MutableSet<Component>, stateSet: MutableSet<State>) {
        val privateSystem = roots.first.privateSubSystem
        while (!roots.isEmpty()) {
            val sExplored = roots.pollFirst()
            stateSet.add(sExplored)
            for (c in sExplored.getComponentsNotAbstracted()!!) {
                if (privateSystem == false && roots.size + stateSet.size > MAX_SUBSYSTEM_SIZE && c.canBeReplacedByInterSystem()) {
                    continue
                }
                if (componentSet.contains(c)) continue
                var noGo = false
                for (sNext in c.getConnectedStates()!!) {
                    if (sNext == null) continue
                    if (sNext.subSystem != null) {
                        noGo = true
                        break
                    }
                    if (sNext.privateSubSystem != privateSystem) {
                        noGo = true
                        break
                    }
                }
                if (noGo) continue
                componentSet.add(c)
                for (sNext in c.getConnectedStates()!!) {
                    if (sNext == null) continue
                    if (stateSet.contains(sNext)) continue
                    roots.addLast(sNext)
                }
            }
        }
    }

    fun breakSystems(sub: SubSystem) {
        if (sub.breakSystem()) {
            for (s in sub.interSystemConnectivity) {
                breakSystems(s)
            }
        }
    }

    companion object {

        const val MAX_SUBSYSTEM_SIZE = 100

        @JvmStatic
        fun main(args: Array<String>) {
            val s = RootSystem(0.1, 1)
            var n1: VoltageState?
            var n2: VoltageState?
            val u1: VoltageSource
            val r1: Resistor
            val r2: Resistor
            s.queuedStates.add(VoltageState().also { n1 = it })
            s.queuedStates.add(VoltageState().also { n2 = it })
            u1 = VoltageSource()
            u1.u = 1.0
            u1.connectTo(n1, null)
            s.queuedComponents.add(u1)
            r1 = Resistor()
            r1.r = 10.0
            r1.connectTo(n1, n2)
            r2 = Resistor()
            r2.r = 20.0
            r2.connectTo(n2, null)
            s.queuedComponents.add(r1)
            s.queuedComponents.add(r2)
            var n11: VoltageState?
            var n12: VoltageState?
            val u11: VoltageSource
            val r11: Resistor
            val r12: Resistor
            val r13: Resistor
            s.queuedStates.add(VoltageState().also { n11 = it })
            s.queuedStates.add(VoltageState().also { n12 = it })
            u11 = VoltageSource()
            u11.u = 1.0
            u11.connectTo(n11, null)
            s.queuedComponents.add(u11)
            r11 = Resistor()
            r11.r = 10.0
            r11.connectTo(n11, n12)
            r12 = Resistor()
            r12.r = 30.0
            r12.connectTo(n12, null)
            s.queuedComponents.add(r11)
            s.queuedComponents.add(r12)
            val i01: InterSystem
            i01 = InterSystem()
            i01.r = 10.0
            i01.connectTo(n2, n12)
            s.queuedComponents.add(i01)
            for (i in 0..49) {
                s.step()
            }
            r13 = Resistor()
            r13.r = 30.0
            r13.connectTo(n12, null)
            s.queuedComponents.add(r13)
            for (i in 0..49) {
                s.step()
            }
            s.step()
            for (sa in s.systems) {
                println(sa)
            }
            println(r11.u)
        }
    }

    fun getSubSystemCount(): Int {
        return systems.size
    }
}