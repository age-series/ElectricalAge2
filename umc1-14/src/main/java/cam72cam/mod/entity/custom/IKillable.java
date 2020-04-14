package cam72cam.mod.entity.custom;

import cam72cam.mod.entity.DamageType;
import cam72cam.mod.entity.Entity;

public interface IKillable {
    IKillable NOP = new IKillable() {
        @Override
        public void onDamage(DamageType type, Entity source, float amount) {

        }

        @Override
        public void onRemoved() {

        }
    };

    static IKillable get(Object o) {
        if (o instanceof IKillable) {
            return (IKillable) o;
        }
        return NOP;
    }

    void onDamage(DamageType type, Entity source, float amount);

    void onRemoved();
}
