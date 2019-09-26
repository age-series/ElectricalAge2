package cam72cam.mod.entity.custom;

import cam72cam.mod.entity.Entity;
import cam72cam.mod.math.Vec3d;

public interface IRidable {
    IRidable NOP = new IRidable() {
        @Override
        public boolean canFitPassenger(Entity passenger) {
            return false;
        }

        @Override
        public boolean shouldRiderSit(Entity passenger) {
            return false;
        }

        @Override
        public Vec3d getMountOffset(Entity passenger, Vec3d offset) {
            return Vec3d.ZERO;
        }

        @Override
        public Vec3d onPassengerUpdate(Entity passenger, Vec3d offset) {
            return Vec3d.ZERO;
        }

        @Override
        public Vec3d onDismountPassenger(Entity passenger, Vec3d offset) {
            return Vec3d.ZERO;
        }
    };

    static IRidable get(Object o) {
        if (o instanceof IRidable) {
            return (IRidable) o;
        }
        return NOP;
    }

    boolean canFitPassenger(Entity passenger);

    boolean shouldRiderSit(Entity passenger);

    Vec3d getMountOffset(Entity passenger, Vec3d offset);

    Vec3d onPassengerUpdate(Entity passenger, Vec3d offset);

    Vec3d onDismountPassenger(Entity passenger, Vec3d offset);
}
