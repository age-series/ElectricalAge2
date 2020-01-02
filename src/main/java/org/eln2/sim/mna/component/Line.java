package org.eln2.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.RootSystem;
import org.eln2.sim.mna.SubSystem;
import org.eln2.sim.mna.misc.ISubSystemProcessFlush;
import org.eln2.sim.mna.state.State;

import java.util.Iterator;
import java.util.LinkedList;

public class Line extends Resistor implements ISubSystemProcessFlush, IAbstractor {

    public LinkedList<Resistor> resistors = new LinkedList<Resistor>(); //from a to b
    public LinkedList<State> states = new LinkedList<State>(); //from a to b

    boolean ofInterSystem;

    boolean canAdd(Component c) {
        return (c instanceof Resistor);
    }

    void add(Resistor c) {
        ofInterSystem |= c.canBeReplacedByInterSystem();
        resistors.add(c);
    }

    @Override
    public boolean canBeReplacedByInterSystem() {
        return ofInterSystem;
    }

    public void recalculateR() {
        double R = 0;
        for (Resistor r : resistors) {
            R += r.getR();
        }

        setR(R);
    }

    void restoreResistorIntoCircuit() {
        //aPin.add(resistors.getFirst());
        //bPin.add(resistors.getLast());
        this.breakConnection();
    }

    void removeResistorFromCircuit() {
        //aPin.remove(resistors.getFirst());
        //bPin.remove(resistors.getLast());
    }

	/*void removeCompFromState(Resistor r, State s) {
		State sNext = (r.aPin == s ? r.bPin : r.aPin);
		if (sNext != null) sNext.remove(r);
	}	
	void addCompFromState(Resistor r, State s) {
		State sNext = (r.aPin == s ? r.bPin : r.aPin);
		if (sNext != null) sNext.add(r);
	}*/

    public static void newLine(RootSystem root, LinkedList<Resistor> resistors, LinkedList<State> states) {
        if (resistors.isEmpty()) {
        } else if (resistors.size() == 1) {
            //root.addComponent(resistors.getFirst());
        } else {
            Resistor first = resistors.getFirst();
            Resistor last = resistors.getLast();
            State stateBefore = first.getAPin() == states.getFirst() ? first.getBPin() : first.getAPin();
            State stateAfter = last.getAPin() == states.getLast() ? last.getBPin() : last.getAPin();
            //stateBefore.remove(first);
            //stateAfter.remove(last);

            Line l = new Line();
            l.resistors = resistors;
            l.states = states;
            l.recalculateR();
            root.addComponents.removeAll(resistors);
            root.addStates.removeAll(states);
            root.addComponents.add(l);
            l.connectTo(stateBefore, stateAfter);
            l.removeResistorFromCircuit();

            root.addProcess(l);

            for (Resistor r : resistors) {
                r.setAbstractedBy(l);
                l.ofInterSystem |= r.canBeReplacedByInterSystem();
            }

            for (State s : states) {
                s.abstractedBy = l;
            }
        }
    }

    @Override
    public void returnToRootSystem(RootSystem root) {
        for (Resistor r : resistors) {
            r.setAbstractedBy(null);
        }

        for (State s : states) {
            s.abstractedBy = null;
        }

        restoreResistorIntoCircuit();

        root.addStates.addAll(states);
        root.addComponents.addAll(resistors);

        root.removeProcess(this);
    }

    @Override
    public void simProcessFlush() {
        double i = (getAPin().state - getBPin().state) * getRInv();
        double u = getAPin().state;
        Iterator<Resistor> ir = resistors.iterator();
        Iterator<State> is = states.iterator();

        while (is.hasNext()) {
            State s = is.next();
            Resistor r = ir.next();
            u -= r.getR() * i;
            s.state = u;
            //u -= r.getR() * i;
        }
    }

    @Override
    public void setSubSystem(SubSystem s) {
        s.addProcess(this);
        super.setSubSystem(s);
    }

    @Override
    public void quitSubSystem() {
    }

    @Override
    public void dirty(Component component) {
        recalculateR();
        if (isAbstracted())
            getAbstractedBy().dirty(this);
    }

    @Override
    public SubSystem getAbstractorSubSystem() {
        return getSubSystem();
    }
}
