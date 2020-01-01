package org.eln.nbt.electrical

import org.eln.sim.mna.state.VoltageState

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

// implements INBTTReady {
class NBTVoltageState(var name: String) : VoltageState()

/*
public void readFromNBT(NBTTagCompound nbttagcompound, String str) {
    setU(nbttagcompound.getFloat(str + name + "Uc"));
    if (Double.isNaN(getU())) setU(0);
    if (getU() == Float.NEGATIVE_INFINITY) setU(0);
    if (getU() == Float.POSITIVE_INFINITY) setU(0);
}

public void writeToNBT(NBTTagCompound nbttagcompound, String str) {
    nbttagcompound.setFloat(str + name + "Uc", (float) getU());
}
*/