package cam72cam.mod.block.tile;

import cam72cam.mod.ModCore;
import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.energy.IEnergy;
import cam72cam.mod.entity.boundingbox.BoundingBox;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.event.CommonEvents;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

public class TileEntity extends net.minecraft.tileentity.TileEntity {
    private static final Map<String, TileEntityType<? extends TileEntity>> types = HashBiMap.create();
    private static final Map<String, Supplier<BlockEntity>> registry = HashBiMap.create();
    private World world;
    private Vec3i pos;
    public boolean hasTileData;

    /*
    Tile registration
    */
    private final BlockEntity instance;

    public TileEntity(Identifier id) {
        super(types.get(id.toString()));
        instance = registry.get(id.toString()).get();
    }

    public static void register(Supplier<BlockEntity> instance, Supplier<TileEntity> ctr, Identifier id) {
        registry.put(id.toString(), instance);
        CommonEvents.Tile.REGISTER.subscribe(() -> {
            TileEntityType<TileEntity> type = new TileEntityType<>(ctr, new HashSet<Block>() {
                public boolean contains(Object var1) {
                    // WHYYYYYYYYYYYYYYYY
                    return true;
                }
            }, null);
            type.setRegistryName(id.internal);
            types.put(id.toString(), type);
            ForgeRegistries.TILE_ENTITIES.register(type);
        });
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
        world.internal.notifyBlockUpdate(super.pos, null, super.world.getBlockState(super.pos), 3);
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
        try {
            this.read(tag);
            this.readUpdate(new TagCompound(tag));
            super.handleUpdateTag(tag);
            world.internal.notifyBlockUpdate(super.pos, null, super.world.getBlockState(super.pos), 3);
        } catch (Exception ex) {
            ModCore.error("IN UPDATE: %s", tag);
            ModCore.catching(ex);
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
                    return target.receive(maxReceive, simulate);
                }

                @Override
                public int extractEnergy(int maxExtract, boolean simulate) {
                    return target.extract(maxExtract, simulate);
                }

                @Override
                public int getEnergyStored() {
                    return target.getCurrent();
                }

                @Override
                public int getMaxEnergyStored() {
                    return target.getMax();
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
        return LazyOptional.empty();
    }

    /*
    Wrapped functionality
    */

    public void setWorld(World world) {
        this.world = world;
        super.setWorld(world.internal);
    }

    public void setPos(Vec3i pos) {
        this.pos = pos;
        super.setPos(pos.internal);
    }

    public void load(TagCompound data) {
        super.read(data.internal);
        pos = new Vec3i(super.pos);

        instance.internal = this;
        instance.world = world;
        instance.pos = pos;

        instance.load(data);
    }

    public void save(TagCompound data) {
        super.write(data.internal);
        instance.save(data);
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

    public BlockEntity instance() {
        if (isLoaded()) {
            this.instance.internal = this;
            this.instance.world = this.world;
            this.instance.pos = this.pos;
            return this.instance;
        }
        return null;
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
    public static ModelProperty<TileEntity> TE_PROPERTY = new ModelProperty<>();
    public final IModelData getModelData() {
        return new ModelDataMap.Builder().withInitial(TE_PROPERTY, this).build();
    }
}
