package cam72cam.mod.block;

import cam72cam.mod.util.Facing;

public interface IRedstoneProvider {
    int getStrongPower(Facing from);

    int getWeakPower(Facing from);
}
