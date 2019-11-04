package cam72cam.mod.block;

import cam72cam.mod.block.tile.TileEntity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.Hand;
import cam72cam.mod.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;

import java.util.function.Supplier;

public abstract class BlockTypeEntity extends BlockType {
    protected final Identifier id;
    private final Supplier<BlockEntity> constructData;

    public BlockTypeEntity(BlockSettings settings, Supplier<BlockEntity> constructData) {
        super(settings.withRedstonePovider(constructData.get() instanceof IRedstoneProvider));
        id = new Identifier(settings.modID, settings.name);
        this.constructData = constructData;
        TileEntity.register(constructData, () -> constructData.get().supplier(id), id);
    }

    public BlockEntity createBlockEntity(World world, Vec3i pos) {
        TileEntity te = ((TileEntity) internal.createTileEntity(null, null));
        te.hasTileData = true;
        te.world = world;
        te.pos = pos;
        return te.instance();
    }

    /*

    BlockType Implementation

    */

    protected BlockInternal getBlock() {
        return new BlockTypeInternal();
    }

    private BlockEntity getInstance(World world, Vec3i pos) {
        TileEntity te = world.getTileEntity(pos, TileEntity.class);
        if (te != null) {
            return te.instance();
        }
        return null;
    }

    @Override
    public final boolean tryBreak(World world, Vec3i pos, Player player) {
        BlockEntity instance = getInstance(world, pos);
        if (instance != null) {
            return instance.tryBreak(player);
        }
        return true;
    }

    /*

    Add block data to normal block calls

     */

    @Override
    public final void onBreak(World world, Vec3i pos) {
        BlockEntity instance = getInstance(world, pos);
        if (instance != null) {
            instance.onBreak();
        }
    }

    @Override
    public final boolean onClick(World world, Vec3i pos, Player player, Hand hand, Facing facing, Vec3d hit) {
        BlockEntity instance = getInstance(world, pos);
        if (instance != null) {
            return instance.onClick(player, hand, facing, hit);
        }
        return false;
    }

    @Override
    public final ItemStack onPick(World world, Vec3i pos) {
        BlockEntity instance = getInstance(world, pos);
        if (instance != null) {
            return instance.onPick();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public final void onNeighborChange(World world, Vec3i pos, Vec3i neighbor) {
        BlockEntity instance = getInstance(world, pos);
        if (instance != null) {
            instance.onNeighborChange(neighbor);
        }
    }

    public final double getHeight(World world, Vec3i pos) {
        BlockEntity instance = getInstance(world, pos);
        if (instance != null) {
            return instance.getHeight();
        }
        return 1;
    }

    @Override
    public int getStrongPower(World world, Vec3i pos, Facing from) {
        if (settings.redstoneProvider) {
            BlockEntity instance = getInstance(world, pos);
            if (instance instanceof IRedstoneProvider) {
                return ((IRedstoneProvider)instance).getStrongPower(from);
            }
        }
        return 0;
    }

    @Override
    public int getWeakPower(World world, Vec3i pos, Facing from) {
        if (settings.redstoneProvider) {
            BlockEntity instance = getInstance(world, pos);
            if (instance instanceof IRedstoneProvider) {
                return ((IRedstoneProvider)instance).getWeakPower(from);
            }
        }
        return 0;
    }

    protected class BlockTypeInternal extends BlockInternal {
        @Override
        public final boolean hasTileEntity(BlockState state) {
            return true;
        }

        @Override
        public final net.minecraft.tileentity.TileEntity createTileEntity(BlockState state, IBlockReader world) {
            return constructData.get().supplier(id);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, IBlockReader source, BlockPos pos, ISelectionContext context) {
            net.minecraft.tileentity.TileEntity entity = source.getTileEntity(pos);
            if (entity == null) {
                return super.getCollisionShape(state, source, pos, context);
            }
            return VoxelShapes.create(new AxisAlignedBB(0.0F, 0.0F, 0.0F, 1.0F, BlockTypeEntity.this.getHeight(World.get(entity.getWorld()), new Vec3i(pos)), 1.0F));
        }

        @Override
        public VoxelShape getShape(BlockState state, IBlockReader source, BlockPos pos, ISelectionContext context) {
            net.minecraft.tileentity.TileEntity entity = source.getTileEntity(pos);
            if (entity == null) {
                return super.getShape(state, source, pos, context);
            }
            return VoxelShapes.create(new AxisAlignedBB(0.0F, 0.0F, 0.0F, 1.0F, Math.max(BlockTypeEntity.this.getHeight(World.get(entity.getWorld()), new Vec3i(pos)), 0.25), 1.0F));
        }
    }


}
