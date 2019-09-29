package cam72cam.mod.block;

import cam72cam.mod.ModCore;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.Hand;
import cam72cam.mod.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = ModCore.MODID)
public abstract class BlockType {
    private static List<Consumer<RegistryEvent.Register<Block>>> registrations = new ArrayList<>();
    public final net.minecraft.block.Block internal;
    protected final BlockSettings settings;

    public BlockType(BlockSettings settings) {
        this.settings = settings;

        internal = getBlock();

        registrations.add(reg -> reg.getRegistry().register(internal));
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        registrations.forEach(reg -> reg.accept(event));
    }

    @SubscribeEvent
    public static void onBlockBreakEvent(BlockEvent.BreakEvent event) {
        Block block = event.getWorld().getBlockState(event.getPos()).getBlock();
        if (block instanceof BlockInternal) {
            BlockInternal internal = (BlockInternal) block;
            if (!internal.tryBreak(event.getWorld(), event.getPos(), event.getPlayer())) {
                event.setCanceled(true);
                //TODO updateListeners?
            }
        }
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
        /* TODO REDSTONE!!!

        @Override
        public int getWeakPower(BlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side)
        {
            if (settings.entity == null) {
                return 0;
            }
            World world = World.get((net.minecraft.world.World) blockAccess);
            net.minecraft.tileentity.TileEntity ent =  world.getTileEntity(new Vec3i(pos), net.minecraft.tileentity.TileEntity.class);
            if (ent instanceof IRedstoneProvider) {
                IRedstoneProvider provider = (IRedstoneProvider) ent;
                return provider.getRedstoneLevel();
            }
            return 0;
        }

        @Override
        public int getStrongPower(BlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side)
        {
            return this.getWeakPower(blockState, blockAccess, pos, side);
        }

        @Override
        public boolean canProvidePower(BlockState state)
        {
            return true;
        }
        */

            /* TODO
            @SideOnly(Side.CLIENT)
            public BlockRenderLayer getBlockLayer() {
                return BlockRenderLayer.CUTOUT_MIPPED;
            }
            */

    }
}
