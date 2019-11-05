package cam72cam.mod.render;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Player;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.item.ItemBase;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.CollectionUtil;
import cam72cam.mod.util.Hand;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class GlobalRender {
    private static List<Consumer<Float>> renderFuncs = new ArrayList<>();

    public static void registerClientEvents() {
        ClientEvents.REGISTER_ENTITY.subscribe(() -> {
            ClientRegistry.bindTileEntitySpecialRenderer(GlobalRenderHelper.class, new TileEntityRenderer<GlobalRenderHelper>() {
                @Override
                public void render(GlobalRenderHelper te, double x, double y, double z, float partialTicks, int destroyStage) {
                    renderFuncs.forEach(r -> r.accept(partialTicks));
                }
            });
        });

        List<TileEntity> grhList = CollectionUtil.listOf(new GlobalRenderHelper());
        ClientEvents.TICK.subscribe(() -> Minecraft.getInstance().worldRenderer.updateTileEntities(grhList, grhList));

        ClientEvents.RENDER_DEBUG.subscribe(event -> {
            if (Minecraft.getInstance().gameSettings.showDebugInfo && GPUInfo.hasGPUInfo()) {
                int i;
                for (i = 0; i < event.getRight().size(); i++) {
                    if (event.getRight().get(i).startsWith("Display: ")) {
                        i++;
                        break;
                    }
                }
                event.getRight().add(i, GPUInfo.debug());
            }
        });
    }

    public static void registerRender(Consumer<Float> func) {
        renderFuncs.add(func);
    }

    public static void registerOverlay(Consumer<Float> func) {
        ClientEvents.RENDER_OVERLAY.subscribe(event -> {
            if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
                func.accept(event.getPartialTicks());
            }
        });
    }

    public static void registerItemMouseover(ItemBase item, MouseoverEvent fn) {
        ClientEvents.RENDER_MOUSEOVER.subscribe(partialTicks -> {
            if (MinecraftClient.getBlockMouseOver() != null) {
                Player player = MinecraftClient.getPlayer();
                if (item.internal == player.getHeldItem(Hand.PRIMARY).internal.getItem()) {
                    fn.render(player, player.getHeldItem(Hand.PRIMARY), MinecraftClient.getBlockMouseOver(), MinecraftClient.getPosMouseOver(), partialTicks);
                }
            }
        });
    }

    public static boolean isTransparentPass() {
        return false;//MinecraftForgeClient.getRenderPass() != 0;
    }

    public static Vec3d getCameraPos(float partialTicks) {
        net.minecraft.entity.Entity playerrRender = Minecraft.getInstance().getRenderViewEntity();
        double d0 = playerrRender.lastTickPosX + (playerrRender.posX - playerrRender.lastTickPosX) * partialTicks;
        double d1 = playerrRender.lastTickPosY + (playerrRender.posY - playerrRender.lastTickPosY) * partialTicks;
        double d2 = playerrRender.lastTickPosZ + (playerrRender.posZ - playerrRender.lastTickPosZ) * partialTicks;
        return new Vec3d(d0, d1, d2);
    }

    static ICamera getCamera(float partialTicks) {
        ICamera camera = new Frustum();
        Vec3d cameraPos = getCameraPos(partialTicks);
        camera.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        return camera;
    }

    public static boolean isInRenderDistance(Vec3d pos) {
        // max rail length is 100, 50 is center
        return MinecraftClient.getPlayer().getPosition().distanceTo(pos) < ((Minecraft.getInstance().gameSettings.renderDistanceChunks + 1) * 16 + 50);
    }

    public static void mulMatrix(FloatBuffer fbm) {
        GL11.glMultMatrixf(fbm);
    }

    @FunctionalInterface
    public interface MouseoverEvent {
        void render(Player player, ItemStack stack, Vec3i pos, Vec3d offset, float partialTicks);
    }

    public static class GlobalRenderHelper extends TileEntity {

        public GlobalRenderHelper() {
            super(new TileEntityType<GlobalRenderHelper>(GlobalRenderHelper::new, new HashSet<>(), null) {
                @Override
                public boolean isValidBlock(Block block_1) {
                    return true;
                }
            });
        }

        @Override
        public boolean hasWorld() {
            return true;
        }

        public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
            return INFINITE_EXTENT_AABB;
        }

        public double getDistanceSq(double x, double y, double z) {
            return 1;
        }
    }
}
