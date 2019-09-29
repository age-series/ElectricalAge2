package cam72cam.mod.entity;

import cam72cam.mod.entity.boundingbox.BoundingBox;
import cam72cam.mod.entity.custom.*;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.net.Packet;
import cam72cam.mod.util.Hand;
import cam72cam.mod.util.TagCompound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SSpawnObjectPacket;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import java.util.*;
import java.util.stream.Collectors;

public class ModdedEntity extends Entity implements IEntityAdditionalSpawnData {
    private cam72cam.mod.entity.Entity self;

    private Map<UUID, Vec3d> passengerPositions = new HashMap<>();
    private List<SeatEntity> seats = new ArrayList<>();

    private EntitySettings settings;
    private String type;
    private IWorldData iWorldData;
    private ISpawnData iSpawnData;
    private ITickable iTickable;
    private IClickable iClickable;
    private IKillable iKillable;
    private IRidable iRidable;
    private ICollision iCollision;

    public ModdedEntity(EntityType type, World world) {
        super(type, world);

        super.preventEntitySpawning = true;
    }

    /* Init Self Wrapper */

    protected final void init(String type) {
        if (self == null) {
            this.type = type;
            self = EntityRegistry.create(type, this);

            EntitySettings settings = EntityRegistry.getSettings(type);
            super.entityCollisionReduction = settings.entityCollisionReduction;
            this.settings = settings;

            iWorldData = IWorldData.get(self);
            iSpawnData = ISpawnData.get(self);
            iTickable = ITickable.get(self);
            iClickable = IClickable.get(self);
            iKillable = IKillable.get(self);
            iRidable = IRidable.get(self);
            iCollision = ICollision.get(self);
        }
    }

    private final void loadSelf(TagCompound data) {
        init(data.getString("custom_mob_type"));
    }

    private final void saveSelf(TagCompound data) {
        data.setString("custom_mob_type", type);
    }

    public cam72cam.mod.entity.Entity getSelf() {
        return self;
    }

    /* IWorldData */

    @Override
    public final void readAdditional(CompoundNBT compound) {
        load(new TagCompound(compound));
    }

    private final void load(TagCompound data) {
        loadSelf(data);
        iWorldData.load(data);
        readPassengerData(data);
    }

    @Override
    public void writeAdditional(CompoundNBT compound) {
        save(new TagCompound(compound));
    }

    private final void save(TagCompound data) {
        iWorldData.save(data);
        saveSelf(data);
        writePassengerData(data);
    }

    /* ISpawnData */

    @Override
    public final void readSpawnData(PacketBuffer additionalData) {
        TagCompound data = new TagCompound(additionalData.readCompoundTag());
        loadSelf(data);
        iSpawnData.loadSpawn(data);
        self.sync.receive(data.get("sync"));
        readPassengerData(data);
    }

    @Override
    public final void writeSpawnData(PacketBuffer buffer) {
        TagCompound data = new TagCompound();
        iSpawnData.saveSpawn(data);
        saveSelf(data);
        data.set("sync", self.sync);
        writePassengerData(data);

        buffer.writeCompoundTag(data.internal);
    }

    /* ITickable */

    @Override
    public final void tick() {
        iTickable.onTick();
        self.sync.send();

        if (!seats.isEmpty()) {
            seats.removeAll(seats.stream().filter(x -> !x.isAlive()).collect(Collectors.toList()));
        }
    }

    /* Player Interact */

    @Override
    public final boolean processInitialInteract(PlayerEntity player, net.minecraft.util.Hand hand) {
        return iClickable.onClick(new Player(player), Hand.from(hand)) == ClickResult.ACCEPTED;
    }

    /* Death */

    @Override
    public final boolean attackEntityFrom(DamageSource damagesource, float amount) {
        cam72cam.mod.entity.Entity wrapEnt = new cam72cam.mod.entity.Entity(damagesource.getTrueSource());
        DamageType type;
        if (damagesource.isExplosion() && !(damagesource.getTrueSource() instanceof MobEntity)) {
            type = DamageType.EXPLOSION;
        } else if (damagesource.getTrueSource() instanceof PlayerEntity) {
            type = damagesource.isProjectile() ? DamageType.PROJECTILE : DamageType.PLAYER;
        } else {
            type = DamageType.OTHER;
        }
        iKillable.onDamage(type, wrapEnt, amount);

        return false;
    }

    @Override
    protected void registerData() {

    }

    @Override
    public final void remove() {
        if (this.isAlive()) {
            super.remove();
            iKillable.onRemoved();
        }
    }

    /* Ridable */

    @Override
    public boolean canFitPassenger(Entity passenger) {
        return iRidable.canFitPassenger(new cam72cam.mod.entity.Entity(passenger));
    }

    private Vec3d calculatePassengerOffset(cam72cam.mod.entity.Entity passenger) {
        return passenger.getPosition().subtract(self.getPosition()).rotateMinecraftYaw(-self.getRotationYaw());
    }

    private Vec3d calculatePassengerPosition(Vec3d offset) {
        return offset.rotateMinecraftYaw(-self.getRotationYaw()).add(self.getPosition());
    }

    @Override
    public final void addPassenger(Entity entity) {
        if (!world.isRemote) {
            System.out.println("New Seat");
            SeatEntity seat = new SeatEntity(SeatEntity.TYPE, world);
            seat.setParent(this);
            cam72cam.mod.entity.Entity passenger = self.getWorld().getEntity(entity);
            passengerPositions.put(entity.getUniqueID(), iRidable.getMountOffset(passenger, calculatePassengerOffset(passenger)));
            entity.startRiding(seat);
            updateSeat(seat);
            world.addEntity(seat);
            self.sendToObserving(new PassengerPositionsPacket(this));
        } else {
            System.out.println("skip");
        }
    }

    List<cam72cam.mod.entity.Entity> getActualPassengers() {
        return seats.stream()
                .map(SeatEntity::getEntityPassenger)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    void updateSeat(SeatEntity seat) {
        if (!seats.contains(seat)) {
            seats.add(seat);
        }

        cam72cam.mod.entity.Entity passenger = seat.getEntityPassenger();
        if (passenger != null) {
            Vec3d offset = passengerPositions.get(passenger.getUUID());
            if (offset != null) {
                offset = iRidable.onPassengerUpdate(passenger, offset);
                passengerPositions.put(passenger.getUUID(), offset);

                Vec3d pos = calculatePassengerPosition(offset);
                seat.setPosition(pos.x, pos.y, pos.z);
                passenger.setPosition(pos);

                float delta = rotationYaw - prevRotationYaw;
                passenger.internal.rotationYaw = passenger.internal.rotationYaw + delta;

                seat.shouldSit = iRidable.shouldRiderSit(passenger);
            }
        }
    }

    boolean isPassenger(cam72cam.mod.entity.Entity passenger) {
        return getActualPassengers().stream().anyMatch(p -> p.getUUID().equals(passenger.getUUID()));
    }

    void removeSeat(SeatEntity seat) {
        cam72cam.mod.entity.Entity passenger = seat.getEntityPassenger();
        if (passenger != null) {
            Vec3d offset = passengerPositions.get(passenger.getUUID());
            if (offset != null) {
                offset = iRidable.onDismountPassenger(passenger, offset);
                passenger.setPosition(calculatePassengerPosition(offset));
            }
        }
        seats.remove(seat);
    }

    void removePassenger(cam72cam.mod.entity.Entity passenger) {
        for (SeatEntity seat : this.seats) {
            cam72cam.mod.entity.Entity seatPass = seat.getEntityPassenger();
            if (seatPass != null && seatPass.getUUID().equals(passenger.getUUID())) {
                passenger.internal.stopRiding();
                break;
            }
        }
    }

    @Override
    public boolean canRiderInteract() {
        return false;
    }

    public int getPassengerCount() {
        return seats.size();
    }

    private void readPassengerData(TagCompound data) {
        passengerPositions = data.getMap("passengers", UUID::fromString, (TagCompound tag) -> tag.getVec3d("pos"));
        // TODO legacy? staticPassengers = data.getList("staticPassengers", StaticPassenger::new);
    }

    private void writePassengerData(TagCompound data) {
        data.setMap("passengers", passengerPositions, UUID::toString, (Vec3d pos) -> {
            //TODO single encoder step
            TagCompound tmp = new TagCompound();
            tmp.setVec3d("pos", pos);
            return tmp;
        });
    }

    /* ICollision */
    @Override
    public AxisAlignedBB getCollisionBoundingBox() {
        return new BoundingBox(iCollision.getCollision());
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return new BoundingBox(iCollision.getCollision());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB bb = this.getBoundingBox();
        return new AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
    }

    @Override
    public IPacket<?> createSpawnPacket() {
        return new SSpawnObjectPacket(this);
    }

    /* Hacks */
    @Override
    public boolean canBeCollidedWith() {
        // Needed for right click, probably a forge or MC bug
        return true;
    }

    @Override
    public boolean canBePushed() {
        return settings.canBePushed;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        if (settings.defaultMovement) {
            super.setPositionAndRotationDirect(x, y, z, yaw, pitch, posRotationIncrements, teleport);
        }
    }

    @Override
    public void setVelocity(double x, double y, double z) {
        if (settings.defaultMovement) {
            super.setVelocity(x, y, z);
        }
    }

    /*
     * Disable standard entity sync
     */

    public static class PassengerPositionsPacket extends Packet {
        public PassengerPositionsPacket() {
            // Forge Reflection
        }

        public PassengerPositionsPacket(ModdedEntity stock) {
            data.setEntity("stock", stock.self);

            stock.writePassengerData(data);
        }

        @Override
        public void handle() {
            cam72cam.mod.entity.Entity entity = data.getEntity("stock", getWorld());
            if (entity != null && entity.internal instanceof ModdedEntity) {
                ModdedEntity stock = (ModdedEntity) entity.internal;
                stock.readPassengerData(data);
            }
        }
    }

    /*
     * TODO!!!
     */
    /*
    //@Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return false;//super.hasCapability(energyCapability, facing);
    }

    @SuppressWarnings("unchecked")
	//@Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) cargoItems;
        }
        return null;//super.getCapability(energyCapability, facing);
    }

	@Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
	@Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) theTank;
        }
        return super.getCapability(capability, facing);
    }
     */
}
