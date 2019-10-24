package cam72cam.mod.entity;

import cam72cam.mod.ModCore;
import cam72cam.mod.util.TagCompound;
import cam72cam.mod.world.World;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

public class SeatEntity extends Entity implements IEntityAdditionalSpawnData {
    static final ResourceLocation ID = new ResourceLocation(ModCore.MODID, "seat");
    private UUID parent;
    private UUID passenger;
    boolean shouldSit = true;
    private boolean hasHadPassenger = false;
    private int ticks = 0;

    public SeatEntity(net.minecraft.world.World worldIn) {
        super(worldIn);
    }

    @Override
    protected void entityInit() {

    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        TagCompound data = new TagCompound(compound);
        parent = data.getUUID("parent");
        passenger = data.getUUID("passenger");
        shouldSit = data.getBoolean("shouldSit");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        TagCompound data = new TagCompound(compound);
        data.setUUID("parent", parent);
        data.setUUID("passenger", passenger);
        data.setBoolean("shouldSit", shouldSit);
    }

    @Override
    public void onUpdate() {
        ticks ++;
        if (world.isRemote || ticks < 5) {
            return;
        }

        if (parent == null) {
            System.out.println("No parent, goodbye");
            this.setDead();
            return;
        }
        if (passenger == null) {
            System.out.println("No passenger, goodbye");
            this.setDead();
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
                this.setDead();
                return;
            }
        }

        if (getParent() == null) {
            if (ticks > 20) {
                System.out.println("No parent found, goodbye");
                this.setDead();
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

    public cam72cam.mod.entity.Entity getEntityPassenger() {
        if (this.isDead) {
            return null;
        }
        if (this.getPassengers().size() == 0) {
            return null;
        }
        return World.get(world).getEntity(getPassengers().get(0));
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        TagCompound data = new TagCompound();
        data.setUUID("parent", parent);
        data.setUUID("passenger", passenger);
        ByteBufUtils.writeTag(buffer, data.internal);
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        TagCompound data = new TagCompound(ByteBufUtils.readTag(additionalData));
        parent = data.getUUID("parent");
        passenger = data.getUUID("passenger");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distance) {
        return false;
    }
}
