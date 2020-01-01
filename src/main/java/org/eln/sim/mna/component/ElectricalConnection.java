package org.eln.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.mna.state.ElectricalLoad;

public class ElectricalConnection extends InterSystem {

    ElectricalLoad L1, L2;

    public ElectricalConnection(ElectricalLoad L1, ElectricalLoad L2) {
        this.L1 = L1;
        this.L2 = L2;
        if(L1 == L2) System.out.println("WARNING: Attempt to connect load to itself?");
    }

    public void notifyRsChange() {
        double R = ((ElectricalLoad) aPin).getRs() + ((ElectricalLoad) bPin).getRs();
        setR(R);
    }

    @Override
    public void onAddToRootSystem() {
        this.connectTo(L1, L2);
    /*	((ElectricalLoad) aPin).electricalConnections.add(this);
		((ElectricalLoad) bPin).electricalConnections.add(this);*/
        notifyRsChange();
    }

    @Override
    public void onRemovefromRootSystem() {
        this.breakConnection();
	/*	((ElectricalLoad) aPin).electricalConnections.remove(this);
		((ElectricalLoad) bPin).electricalConnections.remove(this);*/
    }
}
