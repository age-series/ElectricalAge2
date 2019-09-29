package cam72cam.mod.block.tile;

import cam72cam.mod.ModCore;
import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.block.BlockType;
import cam72cam.mod.energy.IEnergy;
import cam72cam.mod.entity.boundingbox.BoundingBox;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.fluid.ITank;
import cam72cam.mod.item.IInventory;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.TagCompound;
import cam72cam.mod.world.World;
import com.google.common.collect.HashBiMap;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class TileEntity extends net.minecraft.tileentity.TileEntity {
    private static final Map<String, Supplier<BlockEntity>> registry = HashBiMap.create();
    protected static final Map<Class<? extends TileEntity>, Function<TileEntityType, TileEntity>> ctrs = new HashMap<>();
    protected static final Map<Class<? extends TileEntity>, Identifier> names = new HashMap<>();
    private static final Map<Class<? extends TileEntity>, Set<Block>> blocks = new HashMap<>();
    private static final Map<Class<? extends TileEntity>, TileEntityType> types = new HashMap<>();

    static {
        ctrs.put(TileEntity.class, TileEntity::new);
        names.put(TileEntity.class, new Identifier(ModCore.MODID, "hack"));
    }


    public World world;
    public Vec3i pos;
    public boolean hasTileData;
    public String instanceId;

    /*
    Tile registration
    */
    private BlockEntity instance;
    private TagCompound deferredLoad;

    protected TileEntity(TileEntityType type) {
        super(type);
    }

    protected TileEntity(TileEntityType type, Identifier id) {
        super(type);
        instanceId = id.toString();
    }

    public static void register(Class<? extends TileEntity> cls, Supplier<BlockEntity> instance, Identifier id, BlockType type) {
        registry.put(id.toString(), instance);

        if (!blocks.containsKey(cls)) {
            blocks.put(cls, new HashSet<>());
        }
        blocks.get(cls).add(type.internal);
    }

    public static TileEntity construct(Class<? extends TileEntity> cls, Identifier id) {
        TileEntity te = ctrs.get(cls).apply(types.get(cls));
        te.instanceId = id.toString();
        return te;
    }

    @Mod.EventBusSubscriber(modid = ModCore.MODID)
    public static class EventBus {
        @SubscribeEvent
        public static void onTileEntityRegistry(RegistryEvent.Register<TileEntityType<?>> event) {
            for (Class<? extends TileEntity> cls : blocks.keySet()) {
                Function<TileEntityType, TileEntity> ctr = ctrs.get(cls);
                types.put(cls, new TileEntityType<>(() -> ctr.apply(types.get(cls)), blocks.get(cls), null).setRegistryName(names.get(cls).internal));
            }


            types.values().forEach(event.getRegistry()::register);
        }
    }


    /*
    Standard Tile function overrides
    */

    @Override
    public void setWorld(net.minecraft.world.World world) {
        super.setWorld(world);
        this.world = World.get(world);
    }

    @Override
    public void setPos(BlockPos pos) {
        super.setPos(pos);
        this.pos = new Vec3i(pos);
    }


    @Override
    public final void read(CompoundNBT compound) {
        hasTileData = true;
        load(new TagCompound(compound));
    }

    @Override
    public final CompoundNBT write(CompoundNBT compound) {
        save(new TagCompound(compound));
        return compound;
    }

    @Override
    public final SUpdateTileEntityPacket getUpdatePacket() {
        TagCompound nbt = new TagCompound();
        this.write(nbt.internal);
        this.writeUpdate(nbt);

        return new SUpdateTileEntityPacket(this.getPos(), 1, nbt.internal);
    }

    @Override
    public final void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        hasTileData = true;
        this.read(pkt.getNbtCompound());
        this.readUpdate(new TagCompound(pkt.getNbtCompound()));
        super.onDataPacket(net, pkt);
        if (updateRerender()) {
            //TODO? world.internal.markBlockRangeForRenderUpdate(getPos(), getPos());
        }
    }

    @Override
    public final CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.getUpdateTag();
        if (this.isLoaded()) {
            this.write(tag);
            this.writeUpdate(new TagCompound(tag));
        }
        return tag;
    }

    @Override
    public final void handleUpdateTag(CompoundNBT tag) {
        hasTileData = true;
        this.read(tag);
        this.readUpdate(new TagCompound(tag));
        super.handleUpdateTag(tag);
        if (updateRerender()) {
            //TODO? world.internal.markBlockRangeForRenderUpdate(getPos(), getPos());
        }
    }


    @Override
    public void markDirty() {
        super.markDirty();
        if (world.isServer) {
            world.internal.notifyBlockUpdate(getPos(), world.internal.getBlockState(getPos()), world.internal.getBlockState(getPos()), 1 + 2 + 8);
            world.internal.notifyNeighborsOfStateChange(pos.internal, world.internal.getBlockState(getPos()).getBlock());
        }
    }

    /* Forge Overrides */

    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        if (instance() != null) {
            IBoundingBox bb = instance().getBoundingBox();
            if (bb != null) {
                return new BoundingBox(bb);
            }
        }
        return INFINITE_EXTENT_AABB;
    }

    public double getMaxRenderDistanceSquared() {
        return instance() != null ? instance().getRenderDistance() * instance().getRenderDistance() : Integer.MAX_VALUE;
    }

    @Override
    @Nullable
    public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable net.minecraft.util.Direction facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            ITank target = getTank(Facing.from(facing));
            if (target == null) {
                return LazyOptional.empty();
            }

            return LazyOptional.of(() -> new IFluidHandler() {
                @Override
                public int getTanks() {
                    return 1;
                }

                @Nonnull
                @Override
                public FluidStack getFluidInTank(int tank) {
                    return target.getContents().internal;
                }

                @Override
                public int getTankCapacity(int tank) {
                    return target.getCapacity();
                }

                @Override
                public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
                    return target.allows(Fluid.getFluid(stack.getFluid()));
                }

                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    return target.fill(new cam72cam.mod.fluid.FluidStack(resource), action.execute());
                }

                @Nonnull
                @Override
                public FluidStack drain(FluidStack resource, FluidAction action) {
                    return target.drain(new cam72cam.mod.fluid.FluidStack(resource), action.execute()).internal;
                }

                @Nonnull
                @Override
                public FluidStack drain(int maxDrain, FluidAction action) {
                    if (target.getContents().internal == null) {
                        return FluidStack.EMPTY;
                    }
                    return target.drain(new cam72cam.mod.fluid.FluidStack(new FluidStack(target.getContents().internal, maxDrain)), action.execute()).internal;
                }
            }).cast();
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            IInventory target = getInventory(Facing.from(facing));
            if (target == null) {
                return LazyOptional.empty();
            }
            return LazyOptional.of(() -> new IItemHandlerModifiable() {
                @Override
                public int getSlots() {
                    return target.getSlotCount();
                }

                @Override
                public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
                    target.set(slot, new cam72cam.mod.item.ItemStack(stack));
                }

                @Nonnull
                @Override
                public ItemStack getStackInSlot(int slot) {
                    return target.get(slot).internal;
                }

                @Nonnull
                @Override
                public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                    return target.insert(slot, new cam72cam.mod.item.ItemStack(stack), simulate).internal;
                }

                @Nonnull
                @Override
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    return target.extract(slot, amount, simulate).internal;
                }

                @Override
                public int getSlotLimit(int slot) {
                    return target.getLimit(slot);
                }

                @Override
                public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                    return true; //TODO 1.14.4
                }
            }).cast();
        }
        if (capability == CapabilityEnergy.ENERGY) {
            IEnergy target = getEnergy(Facing.from(facing));
            if (target == null) {
                return LazyOptional.empty();
            }
            return LazyOptional.of(() -> new IEnergyStorage() {
                @Override
                public int receiveEnergy(int maxReceive, boolean simulate) {
                    return target.receiveEnergy(maxReceive, simulate);
                }

                @Override
                public int extractEnergy(int maxExtract, boolean simulate) {
                    return target.extractEnergy(maxExtract, simulate);
                }

                @Override
                public int getEnergyStored() {
                    return target.getEnergyStored();
                }

                @Override
                public int getMaxEnergyStored() {
                    return target.getMaxEnergyStored();
                }

                @Override
                public boolean canExtract() {
                    return true;
                }

                @Override
                public boolean canReceive() {
                    return true;
                }
            }).cast();
        }
        return null;
    }

    /*
    Wrapped functionality
    */

    public void setWorld(World world) {
        super.setWorld(world.internal);
    }

    public void setPos(Vec3i pos) {
        super.setPos(pos.internal);
    }

    public void load(TagCompound data) {
        super.read(data.internal);
        pos = new Vec3i(super.pos);

        if (instanceId == null) {
            // If this fails something is really wrong
            instanceId = data.getString("instanceId");
            if (instanceId == null) {
                throw new RuntimeException("Unable to load instanceid with " + data.toString());
            }
        }

        if (instance() != null) {
            instance().load(data);
        } else {
            deferredLoad = data;
        }
    }

    public void save(TagCompound data) {
        super.write(data.internal);
        data.setString("instanceId", instanceId);
        if (instance() != null) {
            instance().save(data);
        }
    }

    public void writeUpdate(TagCompound nbt) {
        if (instance() != null) {
            instance().writeUpdate(nbt);
        }
    }

    public void readUpdate(TagCompound nbt) {
        if (instance() != null) {
            instance().readUpdate(nbt);
        }
    }

    /*
    New Functionality
    */

    public boolean isLoaded() {
        return this.hasWorld() && (world.isServer || hasTileData);
    }

    // TODO render system?
    public boolean updateRerender() {
        return false;
    }

    public BlockEntity instance() {
        if (this.instance == null) {
            if (isLoaded()) {
                if (this.instanceId == null) {
                    System.out.println("WAT NULL");
                }
                if (!registry.containsKey(instanceId)) {
                    System.out.println("WAT " + instanceId);
                }
                this.instance = registry.get(this.instanceId).get();
                this.instance.internal = this;
                this.instance.world = this.world;
                this.instance.pos = this.pos;
                if (deferredLoad != null) {
                    this.instance.load(deferredLoad);
                }
                this.deferredLoad = null;
            }
        }
        return this.instance;
    }

    /* Capabilities */

    public IInventory getInventory(Facing side) {
        return instance() != null ? instance().getInventory(side) : null;
    }

    public ITank getTank(Facing side) {
        return instance() != null ? instance().getTank(side) : null;
    }

    public IEnergy getEnergy(Facing side) {
        return instance() != null ? instance().getEnergy(side) : null;
    }

    /* Render */
    ModelProperty<TileEntity> TE_PROPERTY = new ModelProperty<>();
    public final IModelData getModelData() {
        return new ModelDataMap.Builder().withInitial(TE_PROPERTY, this).build();
    }
}
