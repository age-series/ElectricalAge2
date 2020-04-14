package cam72cam.mod.entity;

import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.entity.sync.EntitySync;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.net.Packet;
import cam72cam.mod.world.World;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.world.Explosion;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Entity {
    public final EntitySync sync;
    public net.minecraft.entity.Entity internal;
    private ModdedEntity modded;

    protected Entity() {
        this.sync = new EntitySync(this);
    }

    public Entity(net.minecraft.entity.Entity entity) {
        this();
        setup(entity);
    }

    Entity setup(net.minecraft.entity.Entity entity) {
        this.internal = entity;
        this.modded = entity instanceof ModdedEntity ? (ModdedEntity) entity : null;
        return this;
    }

    public String tryJoinWorld() {
        return null;
    }

    public World getWorld() {
        return World.get(internal.world);
    }

    public UUID getUUID() {
        return internal.getUniqueID();
    }

    /* Position / Rotation */

    public Vec3i getBlockPosition() {
        return new Vec3i(internal.getPosition());
    }

    public Vec3d getPosition() {
        return new Vec3d(internal.getPositionVector());
    }

    public void setPosition(Vec3d pos) {
        internal.setPosition(pos.x, pos.y, pos.z);
    }

    public Vec3d getVelocity() {
        return new Vec3d(internal.getMotion());
    }

    public void setVelocity(Vec3d motion) {
        internal.setMotion(motion.internal);
    }

    public float getRotationYaw() {
        return internal.rotationYaw;
    }

    public void setRotationYaw(float yaw) {
        internal.prevRotationYaw = internal.rotationYaw;
        internal.rotationYaw = yaw;
    }

    public float getRotationPitch() {
        return internal.rotationPitch;
    }

    public void setRotationPitch(float pitch) {
        internal.prevRotationPitch = internal.rotationPitch;
        internal.rotationPitch = pitch;
    }

    public float getPrevRotationYaw() {
        return internal.prevRotationYaw;
    }

    public float getPrevRotationPitch() {
        return internal.prevRotationPitch;
    }

    public Vec3d getPositionEyes(float partialTicks) {
        return new Vec3d(internal.getEyePosition(partialTicks));
    }


    /* Casting */


    public Player asPlayer() {
        if (internal instanceof PlayerEntity) {
            return new Player((PlayerEntity) internal);
        }
        return null;
    }

    public boolean is(Class<? extends net.minecraft.entity.Entity> entity) {
        return entity.isInstance(internal);
    }

    public <T extends net.minecraft.entity.Entity> T asInternal(Class<T> entity) {
        if (internal.getClass().isInstance(entity)) {
            return (T) internal;
        }
        return null;
    }

    public <T extends Entity> T as(Class<T> type) {
        if (type.isInstance(this)) {
            return (T) this;
        }
        return null;
    }

    public boolean isVillager() {
        return this.is(VillagerEntity.class);
    }

    public void kill() {
        internal.remove();
    }

    public final boolean isDead() {
        return !internal.isAlive();
    }


    /* Networking */

    public void sendToObserving(Packet packet) {
        boolean found = false;

        int syncDist = internal.getType().getTrackingRange();
        for (PlayerEntity player : internal.world.getPlayers()) {
            if (player.getPositionVector().distanceTo(internal.getPositionVector()) < syncDist) {
                found = true;
                break;
            }
        }
        if (found) {
            packet.sendToAllAround(getWorld(), getPosition(), syncDist);
        }
    }

    public int getTickCount() {
        return internal.ticksExisted;
    }

    public int getPassengerCount() {
        if (modded != null) {
            return modded.getPassengerCount();
        } else {
            return internal.getPassengers().size();
        }
    }

    public final void addPassenger(cam72cam.mod.entity.Entity entity) {
        entity.internal.startRiding(internal);
    }

    public final boolean isPassenger(cam72cam.mod.entity.Entity passenger) {
        if (modded != null) {
            return modded.isPassenger(passenger);
        }
        return internal.isPassenger(passenger.internal);
    }

    public void removePassenger(Entity entity) {
        if (modded != null) {
            modded.removePassenger(entity);
        }
        entity.internal.stopRiding();
    }

    public List<Entity> getPassengers() {
        if (modded != null) {
            return modded.getActualPassengers();
        }
        return internal.getPassengers().stream().map(Entity::new).collect(Collectors.toList());
    }

    public boolean isPlayer() {
        return internal instanceof PlayerEntity;
    }

    public Entity getRiding() {
        if (internal.getRidingEntity() != null) {
            if (internal.getRidingEntity() instanceof SeatEntity) {
                return ((SeatEntity)internal.getRidingEntity()).getParent();
            }
            return getWorld().getEntity(internal.getRidingEntity());
        }
        return null;
    }

    public IBoundingBox getBounds() {
        return IBoundingBox.from(internal.getBoundingBox());
    }

    public float getRotationYawHead() {
        return internal.getRotationYawHead();
    }

    public Vec3d getLastTickPos() {
        return new Vec3d(internal.lastTickPosX, internal.lastTickPosY, internal.lastTickPosZ);
    }

    public boolean isLiving() {
        return internal instanceof LivingEntity;
    }

    public void startRiding(Entity entity) {
        internal.startRiding(entity.internal);
    }

    public float getRidingSoundModifier() {
        return 1;
    }

    public void directDamage(String msg, double damage) {
        internal.attackEntityFrom((new DamageSource(msg)).setDamageBypassesArmor(), (float) damage);
    }

    protected void createExplosion(Vec3d pos, float size, boolean damageTerrain) {
        Explosion explosion = new Explosion(getWorld().internal, this.internal, pos.x, pos.y, pos.z, size, false, damageTerrain ? Explosion.Mode.DESTROY : Explosion.Mode.NONE);
        if (net.minecraftforge.event.ForgeEventFactory.onExplosionStart(getWorld().internal, explosion)) return;
        explosion.doExplosionA();
        explosion.doExplosionB(true);

    }

    public int getId() {
        return internal.getEntityId();
    }
}
