package cam72cam.mod.energy;

import net.minecraftforge.energy.IEnergyStorage;

public interface IEnergy {
    static IEnergy from(IEnergyStorage internal) {
        return new IEnergy() {
            @Override
            public int receive(int maxReceive, boolean simulate) {
                return internal.receiveEnergy(maxReceive, simulate);
            }

            @Override
            public int extract(int maxExtract, boolean simulate) {
                return internal.extractEnergy(maxExtract, simulate);
            }

            @Override
            public int getCurrent() {
                return internal.getEnergyStored();
            }

            @Override
            public int getMax() {
                return internal.getMaxEnergyStored();
            }
        };
    }

    int receive(int maxReceive, boolean simulate);

    int extract(int maxExtract, boolean simulate);

    int getCurrent();

    int getMax();
}
