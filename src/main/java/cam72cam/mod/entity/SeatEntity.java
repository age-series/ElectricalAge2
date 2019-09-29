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

import java.util.UUID;

public class SeatEntity extends Entity implements IEntityAdditionalSpawnData {
    public static final EntityType<?> TYPE = EntityType.Builder.create(SeatEntity::new, EntityClassification.MISC).immuneToFire().build(SeatEntity.ID.toString());
    static final ResourceLocation ID = new ResourceLocation(ModCore.MODID, "seat");
    private UUID parent;
    private int ticksUnsure = 0;
    boolean shouldSit = true;

    public SeatEntity(EntityType type, net.minecraft.world.World worldIn) {
        super(type, worldIn);
    }

    @Override
    protected void readAdditional(CompoundNBT compound) {
        TagCompound data = new TagCompound(compound);
        parent = data.getUUID("parent");
        shouldSit = data.getBoolean("shouldSit");
    }

    @Override
    protected void writeAdditional(CompoundNBT compound) {
        TagCompound data = new TagCompound(compound);
        data.setUUID("parent", parent);
        data.setBoolean("shouldSit", shouldSit);
    }

    @Override
    protected void registerData() {

    }

    @Override
    public void tick() {
        if (parent == null) {
            System.out.println("No parent, goodbye");
            this.remove();
            return;
        }
        if (getPassengers().isEmpty()) {
            System.out.println("No passengers, goodbye");
            this.remove();
            return;
        }
        if (ticksUnsure > 10) {
            System.out.println("Parent not loaded, goodbye");
            this.remove();
            return;
        }

        cam72cam.mod.entity.Entity linked = World.get(world).getEntity(parent, cam72cam.mod.entity.Entity.class);
        if (linked != null && linked.internal instanceof ModdedEntity) {
            ticksUnsure = 0;
        } else {
            ticksUnsure++;
        }
    }

    public void setParent(ModdedEntity moddedEntity) {
        this.parent = moddedEntity.getUniqueID();
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
        return new SSpawnObjectPacket(this);
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
        buffer.writeCompoundTag(data.internal);
    }

    @Override
    public void readSpawnData(PacketBuffer additionalData) {
        TagCompound data = new TagCompound(additionalData.readCompoundTag());
        parent = data.getUUID("parent");
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return false;
    }
}
