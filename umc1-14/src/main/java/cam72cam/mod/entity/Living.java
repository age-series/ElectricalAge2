package cam72cam.mod.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;

public class Living extends Entity {
    private final LivingEntity living;

    public Living(net.minecraft.entity.LivingEntity entity) {
        super(entity);
        this.living = entity;
    }

    public boolean isLeashedTo(Player player) {
        return living instanceof MobEntity && ((MobEntity) living).getLeashed() && ((MobEntity) living).getLeashHolder().getUniqueID().equals(player.getUUID());
    }

    public void unleash(Player player) {
        if (living instanceof MobEntity) {
            ((MobEntity)living).clearLeashed(true, !player.isCreative());
        }
    }

    public void setLeashHolder(Player player) {
        if (living instanceof MobEntity) {
            ((MobEntity) living).setLeashHolder(player.internal, true);
        }
    }

    public boolean canBeLeashedTo(Player player) {
        return living instanceof MobEntity && ((MobEntity)living).canBeLeashedTo(player.internal);
    }
}
