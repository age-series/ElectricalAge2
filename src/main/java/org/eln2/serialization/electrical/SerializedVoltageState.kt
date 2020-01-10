package org.eln2.serialization.electrical

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.serialization.generic.IHaveState
import org.eln2.serialization.generic.ISerialize
import org.eln2.sim.mna.state.VoltageState

class SerializedVoltageState(var name: String) : VoltageState(), IHaveState {
    override fun save(ss: ISerialize) {
        ss.setDouble(name + "U", u)
    }

    override fun load(ss: ISerialize) {
        u = ss.getDouble(name + "U")?: 0.0
        if (!u.isFinite()) u = 0.0
    }
}