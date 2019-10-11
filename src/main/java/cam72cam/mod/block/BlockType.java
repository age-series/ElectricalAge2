package cam72cam.mod.block;

import cam72cam.mod.entity.Player;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.Hand;
import cam72cam.mod.world.World;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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
            super(settings.material.internal);
            setHardness(settings.hardness);
            setSoundType(settings.material.soundType);
            setUnlocalizedName(settings.modID + ":" + settings.name);
            setRegistryName(new ResourceLocation(settings.modID, settings.name));
        }

        @Override
        public final void breakBlock(net.minecraft.world.World world, BlockPos pos, IBlockState state) {
            BlockType.this.onBreak(World.get(world), new Vec3i(pos));
            super.breakBlock(world, pos, state);
        }

        @Override
        public final boolean onBlockActivated(net.minecraft.world.World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
            return BlockType.this.onClick(World.get(world), new Vec3i(pos), new Player(player), Hand.from(hand), Facing.from(facing), new Vec3d(hitX, hitY, hitZ));
        }

        @Override
        public final net.minecraft.item.ItemStack getPickBlock(IBlockState state, RayTraceResult target, net.minecraft.world.World world, BlockPos pos, EntityPlayer player) {
            return BlockType.this.onPick(World.get(world), new Vec3i(pos)).internal;
        }

        @Override
        public void neighborChanged(IBlockState state, net.minecraft.world.World worldIn, BlockPos pos, net.minecraft.block.Block blockIn, BlockPos fromPos) {
            this.onNeighborChange(worldIn, pos, fromPos);
        }

        @Override
        public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
            BlockType.this.onNeighborChange(World.get((net.minecraft.world.World) world), new Vec3i(pos), new Vec3i(neighbor));
        }

        /*
        Overrides
         */
        @Override
        public final float getExplosionResistance(Entity exploder) {
            return settings.resistance;
        }


        @Override
        public final EnumBlockRenderType getRenderType(IBlockState state) {
            // TESR Renderer TODO OPTIONAL!@!!!!
            return EnumBlockRenderType.MODEL;
        }


        @Override
        public final boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public final boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            return new AxisAlignedBB(0, 0, 0, 1, BlockType.this.getHeight(), 1);
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            return new AxisAlignedBB(0, 0, 0, 1, BlockType.this.getHeight(), 1);
        }

        @Override
        public AxisAlignedBB getSelectedBoundingBox(IBlockState state, net.minecraft.world.World worldIn, BlockPos pos) {
            return getCollisionBoundingBox(state, worldIn, pos).expand(0, 0.1, 0).offset(pos);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return 0;
        }

        /*
         * Fence, glass override
         */
        @Override
        public boolean canBeConnectedTo(IBlockAccess internal, BlockPos pos, EnumFacing facing) {
            return settings.connectable;
        }

        @Deprecated
        @Override
        public BlockFaceShape getBlockFaceShape(IBlockAccess p_193383_1_, IBlockState p_193383_2_, BlockPos p_193383_3_, EnumFacing p_193383_4_) {
            if (settings.connectable) {
                return super.getBlockFaceShape(p_193383_1_, p_193383_2_, p_193383_3_, p_193383_4_);
            }

            if (p_193383_4_ == EnumFacing.UP) {
                // SNOW ONLY?
                return BlockFaceShape.SOLID;
            }
            return BlockFaceShape.UNDEFINED;
        }

        public boolean tryBreak(net.minecraft.world.World world, BlockPos pos, EntityPlayer player) {
            return BlockType.this.tryBreak(World.get(world), new Vec3i(pos), new Player(player));
        }

        /* Redstone */

        @Override
        public int getWeakPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side)
        {
            return settings.redstoneProvider ? BlockType.this.getWeakPower(World.get((net.minecraft.world.World)blockAccess), new Vec3i(pos), Facing.from(side)) : 0;
        }

        @Override
        public int getStrongPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side)
        {
            return settings.redstoneProvider ? BlockType.this.getStrongPower(World.get((net.minecraft.world.World)blockAccess), new Vec3i(pos), Facing.from(side)) : 0;
        }

        @Override
        public boolean canProvidePower(IBlockState state)
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
