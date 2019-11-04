package cam72cam.mod.block;

import cam72cam.mod.entity.Player;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.Hand;
import cam72cam.mod.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraftforge.registries.ForgeRegistries;

public abstract class BlockType {
    public final net.minecraft.block.Block internal;
    protected final BlockSettings settings;

    public BlockType(BlockSettings settings) {
        this.settings = settings;

        internal = getBlock();

        CommonEvents.Block.REGISTER.subscribe(() -> ForgeRegistries.BLOCKS.register(internal));

        CommonEvents.Block.BROKEN.subscribe((world, pos, player) -> {
            net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
            if (block instanceof BlockInternal) {
                return ((BlockInternal) block).tryBreak(world, pos, player);
            }
            return true;
        });
    }

    public String getName() {
        return settings.name;
    }

    protected BlockInternal getBlock() {
        return new BlockInternal();
    }

    public abstract boolean tryBreak(World world, Vec3i pos, Player player);

    /*
    Public functionality
     */

    public abstract void onBreak(World world, Vec3i pos);

    public abstract boolean onClick(World world, Vec3i pos, Player player, Hand hand, Facing facing, Vec3d hit);

    public abstract ItemStack onPick(World world, Vec3i pos);

    public abstract void onNeighborChange(World world, Vec3i pos, Vec3i neighbor);

    public double getHeight() {
        return 1;
    }

    public int getStrongPower(World world, Vec3i vec3i, Facing from) {
        return 0;
    }

    public int getWeakPower(World world, Vec3i vec3i, Facing from) {
        return 0;
    }

    protected class BlockInternal extends net.minecraft.block.Block {
        public BlockInternal() {
            super(Block.Properties.create(settings.material.internal).sound(settings.material.soundType).hardnessAndResistance(settings.hardness));
            setRegistryName(new ResourceLocation(settings.modID, settings.name));
        }

        @Override
        public void onReplaced(BlockState state, net.minecraft.world.World world, BlockPos pos, BlockState newState, boolean isMoving) {
            BlockType.this.onBreak(World.get(world), new Vec3i(pos));
            super.onReplaced(state, world, pos, newState, isMoving);
        }

        @Override
        public boolean onBlockActivated(BlockState state, net.minecraft.world.World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockRayTraceResult hit) {
            return BlockType.this.onClick(World.get(world), new Vec3i(pos), new Player(player), Hand.from(hand), Facing.from(hit.getFace()), new Vec3d(hit.getHitVec()));
        }

        @Override
        public final net.minecraft.item.ItemStack getPickBlock(BlockState state, RayTraceResult target, IBlockReader world, BlockPos pos, PlayerEntity player) {
            return BlockType.this.onPick(World.get((net.minecraft.world.IWorld)world), new Vec3i(pos)).internal;
        }

        @Override
        public void neighborChanged(BlockState state, net.minecraft.world.World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
            this.onNeighborChange(state, worldIn, pos, fromPos);
        }

        @Override
        public void onNeighborChange(BlockState state, IWorldReader world, BlockPos pos, BlockPos neighbor){
            BlockType.this.onNeighborChange(World.get((net.minecraft.world.World) world), new Vec3i(pos), new Vec3i(neighbor));
        }

        /*
        Overrides
         */
        @Override
        public float getExplosionResistance() {
            return settings.resistance;
        }


        @Override
        public BlockRenderType getRenderType(BlockState state) {
            // TESR Renderer TODO OPTIONAL!@!!!!
            return BlockRenderType.MODEL;
        }

        @Override
        public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
            return VoxelShapes.create(new AxisAlignedBB(0, 0, 0, 1, BlockType.this.getHeight(), 1));
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
            return VoxelShapes.create(new AxisAlignedBB(0, 0, 0, 1, BlockType.this.getHeight(), 1));
        }

        @Override
        public VoxelShape getRenderShape(BlockState state, IBlockReader worldIn, BlockPos pos) {
            return VoxelShapes.create(new AxisAlignedBB(0, 0, 0, 1, BlockType.this.getHeight(), 1));
        }

        /*
         * Fence, glass override
         */
        //TODO 1.14.4

        public boolean tryBreak(net.minecraft.world.IWorld world, BlockPos pos, PlayerEntity player) {
            return BlockType.this.tryBreak(World.get(world), new Vec3i(pos), new Player(player));
        }

        /* Redstone */

        @Override
        public int getWeakPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
            return settings.redstoneProvider ? BlockType.this.getWeakPower(World.get((net.minecraft.world.World)blockAccess), new Vec3i(pos), Facing.from(side)) : 0;
        }

        @Override
        public int getStrongPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
            return settings.redstoneProvider ? BlockType.this.getStrongPower(World.get((net.minecraft.world.World)blockAccess), new Vec3i(pos), Facing.from(side)) : 0;
        }

        @Override
        public boolean canProvidePower(BlockState state)
        {
            return settings.redstoneProvider;
        }

        /* TODO
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getBlockLayer() {
            return BlockRenderLayer.CUTOUT_MIPPED;
        }
        */

    }
}
