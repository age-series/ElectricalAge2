package cam72cam.mod.entity;

import cam72cam.mod.ModCore;
import cam72cam.mod.util.TagCompound;
import cam72cam.mod.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SSpawnObjectPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.UUID;

public class SeatEntity extends Entity implements IEntityAdditionalSpawnData {
    static final ResourceLocation ID = new ResourceLocation(ModCore.MODID, "seat");
    public static final EntityType<?> TYPE = EntityType.Builder.create(SeatEntity::new, EntityClassification.MISC).setShouldReceiveVelocityUpdates(false).setTrackingRange(512).setUpdateInterval(20).immuneToFire().build(SeatEntity.ID.toString());
    static {
        TYPE.setRegistryName(ID);
    }
    private UUID parent;
    private UUID passenger;
    boolean shouldSit = true;
    private boolean hasHadPassenger = false;
    private int ticks = 0;

    public SeatEntity(EntityType type, net.minecraft.world.World worldIn) {
        super(type, worldIn);
    }

    @Override
    protected void readAdditional(CompoundNBT compound) {
        TagCompound data = new TagCompound(compound);
        parent = data.getUUID("parent");
        passenger = data.getUUID("passenger");
        shouldSit = data.getBoolean("shouldSit");
    }

    @Override
    protected void writeAdditional(CompoundNBT compound) {
        TagCompound data = new TagCompound(compound);
        data.setUUID("parent", parent);
        data.setUUID("passenger", passenger);
        data.setBoolean("shouldSit", shouldSit);
    }

    @Override
    protected void registerData() {

    }

    @Override
    public void tick() {
        ticks ++;
        if (world.isRemote || ticks < 5) {
            return;
        }

        if (parent == null) {
            System.out.println("No parent, goodbye");
            this.remove();
            return;
        }
        if (passenger == null) {
            System.out.println("No passenger, goodbye");
            this.remove();
            return;
        }

        if (getPassengers().isEmpty()) {
            if (this.ticks < 20) {
                if (!hasHadPassenger) {
                    cam72cam.mod.entity.Entity toRide = World.get(world).getEntity(passenger, cam72cam.mod.entity.Entity.class);
                    if (toRide != null) {
                        System.out.println("FORCE RIDER");
                        toRide.internal.startRiding(this, true);
                        hasHadPassenger = true;
                    }
                }
            } else {
                System.out.println("No passengers, goodbye");
                this.remove();
                return;
            }
        }

        if (getParent() == null) {
            if (ticks > 20) {
                System.out.println("No parent found, goodbye");
                this.remove();
            }
        }
    }

    public void setup(ModdedEntity moddedEntity, Entity passenger) {
        this.parent = moddedEntity.getUniqueID();
        this.setPosition(moddedEntity.posX, moddedEntity.posY, moddedEntity.posZ);
        this.passenger = passenger.getUniqueID();
    }

    public cam72cam.mod.entity.Entity getParent() {
        cam72cam.mod.entity.Entity linked = World.get(world).getEntity(parent, cam72cam.mod.entity.Entity.class);
        if (linked != null && linked.internal instanceof ModdedEntity) {
            return linked;
        }
        return null;
    }

    @Override
    public double getMountedYOffset() {
        return 0;
    }

    @Override
    public final void updatePassenger(net.minecraft.entity.Entity passenger) {
        cam72cam.mod.entity.Entity linked = World.get(world).getEntity(parent, cam72cam.mod.entity.Entity.class);
        if (linked != null && linked.internal instanceof ModdedEntity) {
            ((ModdedEntity) linked.internal).updateSeat(this);
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return shouldSit;
    }

    @Override
    public final void removePassenger(net.minecraft.entity.Entity passenger) {
        cam72cam.mod.entity.Entity linked = World.get(world).getEntity(parent, cam72cam.mod.entity.Entity.class);
        if (linked != null && linked.internal instanceof ModdedEntity) {
            ((ModdedEntity) linked.internal).removeSeat(this);
        }
        super.removePassenger(passenger);
    }

    @Override
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public cam72cam.mod.entity.Entity getEntityPassenger() {
        if (!this.isAlive()) {
            return null;
        }
        if (this.getPassengers().size() == 0) {
            return null;
        }
        return World.get(world).getEntity(getPassengers().get(0));
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {
        TagCompound data = new TagCompound();
        data.setUUID("parent", parent);
        data.setUUID("passenger", passenger);
        buffer.writeCompoundTag(data.internal);
    }

    @Override
    public void readSpawnData(PacketBuffer additionalData) {
        TagCompound data = new TagCompound(additionalData.readCompoundTag());
        parent = data.getUUID("parent");
        passenger = data.getUUID("passenger");
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return false;
    }
}
