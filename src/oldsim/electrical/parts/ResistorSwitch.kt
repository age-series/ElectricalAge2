package org.eln2.oldsim.electrical.parts

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.component.Resistor
import oldsim.electrical.MnaConst
import org.eln2.oldsim.electrical.mna.state.State

class ResistorSwitch: Resistor {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    open var state: Boolean = false
    open var ultraImpedance = false

    private var _r = 1.0
    override var r: Double
        set(r) {_r = r}
        get() {
            return if (state) _r else if (ultraImpedance) MnaConst.ultraImpedance else MnaConst.highImpedance
        }
}

/*
@Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        str += name;
        setR(nbt.getDouble(str + "R"));
        if (Double.isNaN(baseR) || baseR == 0) {
            if (ultraImpedance) ultraImpedance();
            else highImpedance();
        }
        setState(nbt.getBoolean(str + "State"));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt, String str) {
        str += name;
        nbt.setDouble(str + "R", baseR);
        nbt.setBoolean(str + "State", getState());
    }
 */