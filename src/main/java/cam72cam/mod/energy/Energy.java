package cam72cam.mod.energy;

public class Energy implements IEnergy {
    private final int max;
    private int stored;

    public Energy(int maxStorage) {
        this.stored = 0;
        this.max = maxStorage;
    }

    @Override
    public int receive(int maxReceive, boolean simulate) {
        int delta = Math.min(maxReceive, max - stored);
        if (!simulate) {
            this.stored += delta;
        }
        return delta;
    }

    @Override
    public int extract(int maxExtract, boolean simulate) {
        int delta = Math.min(maxExtract, stored);
        if (!simulate) {
            this.stored -= delta;
        }
        return delta;
    }

    @Override
    public int getCurrent() {
        return stored;
    }

    @Override
    public int getMax() {
        return max;
    }
}
