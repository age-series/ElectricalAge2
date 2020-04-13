package cam72cam.mod.util;

import net.minecraft.util.Direction;

public enum Axis {
    X(Direction.Axis.X),
    Y(Direction.Axis.Y),
    Z(Direction.Axis.Z);

    public final Direction.Axis internal;

    Axis(Direction.Axis internal) {
        this.internal = internal;
    }

    public static Axis from(Direction.Axis axis) {
        switch (axis) {
            case X:
                return X;
            case Y:
                return Y;
            case Z:
                return Z;
            default:
                return null;
        }
    }
}
