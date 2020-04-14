package cam72cam.mod.math;

public class Vec3d {
    public static final Vec3d ZERO = new Vec3d(net.minecraft.util.math.Vec3d.ZERO);
    public final net.minecraft.util.math.Vec3d internal;
    public final double x;
    public final double y;
    public final double z;

    public Vec3d(net.minecraft.util.math.Vec3d internal) {
        this.internal = internal;
        this.x = internal.x;
        this.y = internal.y;
        this.z = internal.z;
    }

    public Vec3d(double x, double y, double z) {
        this(new net.minecraft.util.math.Vec3d(x, y, z));
    }

    public Vec3d(Vec3i pos) {
        this(pos.x, pos.y, pos.z);
    }

    public Vec3d add(double x, double y, double z) {
        return new Vec3d(internal.add(x, y, z));
    }

    public Vec3d add(Vec3i offset) {
        return new Vec3d(internal.add(offset.x, offset.y, offset.z));
    }

    public Vec3d add(Vec3d other) {
        return new Vec3d(internal.add(other.internal));
    }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(internal.subtract(other.internal));
    }

    public Vec3d subtract(Vec3i offset) {
        return new Vec3d(internal.subtract(offset.x, offset.y, offset.z));
    }

    public Vec3d subtract(double x, double y, double z) {
        return new Vec3d(internal.subtract(x, y, z));
    }

    public double length() {
        return internal.length();
    }

    public double distanceTo(Vec3d other) {
        return internal.distanceTo(other.internal);
    }

    public Vec3d scale(double scale) {
        return new Vec3d(internal.scale(scale));
    }

    public Vec3d normalize() {
        return new Vec3d(internal.normalize());
    }

    public Vec3d min(Vec3d other) {
        return new Vec3d(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z));
    }

    public Vec3d max(Vec3d other) {
        return new Vec3d(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z));
    }

    @Override
    public String toString() {
        return internal.toString();
    }

    public Vec3d rotateMinecraftYaw(float angleDegrees) {
        double rad = Math.toRadians(angleDegrees);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new Vec3d(cos * -x + sin * z, y, sin * x + cos * z);
    }

    public Vec3d rotateYaw(float angleDegrees) {
        double rad = Math.toRadians(angleDegrees);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new Vec3d(cos * x + sin * z, y, sin * x + cos * z);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Vec3d && internal.equals(((Vec3d) other).internal);
    }
}
