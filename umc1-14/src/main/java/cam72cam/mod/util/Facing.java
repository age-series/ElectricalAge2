package cam72cam.mod.util;

import cam72cam.mod.math.Rotation;
import net.minecraft.util.Direction;

public enum Facing {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST),
    ;

    public static final Facing[] HORIZONTALS = {
            NORTH, SOUTH, EAST, WEST
    };
    public final Direction internal;

    Facing(Direction internal) {
        this.internal = internal;
    }

    public static Facing from(Direction facing) {
        if (facing == null) {
            return null;
        }
        switch (facing) {
            case DOWN:
                return DOWN;
            case UP:
                return UP;
            case NORTH:
                return NORTH;
            case SOUTH:
                return SOUTH;
            case WEST:
                return WEST;
            case EAST:
                return EAST;
            default:
                return null;
        }
    }

    @Deprecated
    public static Facing from(byte facing) {
        return from(net.minecraft.util.Direction.byIndex(facing));
    }

    public static Facing fromAngle(float v) {
        return from(Direction.fromAngle(v));
    }

    public Facing getOpposite() {
        switch (this) {
            case DOWN:
                return UP;
            case UP:
                return DOWN;
            case NORTH:
                return SOUTH;
            case SOUTH:
                return NORTH;
            case WEST:
                return EAST;
            case EAST:
                return WEST;
            default:
                return null;
        }
    }

    public Facing rotate(Rotation rot) {
        return Facing.from(rot.internal.rotate(this.internal));
    }

    public float getHorizontalAngle() {
        return internal.getHorizontalAngle();
    }

    public Axis getAxis() {
        return Axis.from(internal.getAxis());
    }
}
