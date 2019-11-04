package cam72cam.mod.render;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.ModdedEntity;
import cam72cam.mod.entity.SeatEntity;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.world.World;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityRenderer extends Render<ModdedEntity> {
    private static Map<Class<? extends Entity>, IEntityRender> renderers = new HashMap<>();

    static {
        GlobalRender.registerRender(EntityRenderer::renderLargeEntities);
    }

    public static void registerClientEvents() {
        ClientEvents.REGISTER_ENTITY.subscribe(() -> RenderingRegistry.registerEntityRenderingHandler(ModdedEntity.class, EntityRenderer::new));

        ClientEvents.REGISTER_ENTITY.subscribe(() -> RenderingRegistry.registerEntityRenderingHandler(SeatEntity.class, manager -> new Render<SeatEntity>(manager) {
            @Nullable
            @Override
            protected ResourceLocation getEntityTexture(SeatEntity entity) {
                return null;
            }
        }));
    }

    public EntityRenderer(RenderManager factory) {
        super(factory);
    }

    public static void register(Class<? extends Entity> type, IEntityRender render) {
        renderers.put(type, render);
    }

    private static void renderLargeEntities(float partialTicks) {
        if (GlobalRender.isTransparentPass()) {
            return;
        }

        Minecraft.getMinecraft().mcProfiler.startSection("large_entity_helper");

        ICamera camera = GlobalRender.getCamera(partialTicks);

        World world = MinecraftClient.getPlayer().getWorld();
        List<Entity> entities = world.getEntities(Entity.class);
        for (Entity entity : entities) {
            // Duplicate forge logic and render entity if the chunk is not rendered but entity is visible (MC entitysize issues/optimization)
            AxisAlignedBB chunk = new AxisAlignedBB(entity.getBlockPosition().toChunkMin().internal, entity.getBlockPosition().toChunkMax().internal);
            if (!camera.isBoundingBoxInFrustum(chunk) && camera.isBoundingBoxInFrustum(entity.internal.getRenderBoundingBox())) {
                Minecraft.getMinecraft().getRenderManager().renderEntityStatic(entity.internal, partialTicks, true);
            }
        }

        Minecraft.getMinecraft().mcProfiler.endSection();
    }

    @Override
    public void doRender(ModdedEntity stock, double x, double y, double z, float entityYaw, float partialTicks) {
        Entity self = stock.getSelf();

        GL11.glPushMatrix();
        {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(180 - entityYaw, 0, 1, 0);
            GL11.glRotatef(self.getRotationPitch(), 1, 0, 0);
            GL11.glRotatef(-90, 0, 1, 0);
            renderers.get(self.getClass()).render(self, partialTicks);
        }
        GL11.glPopMatrix();

    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(ModdedEntity entity) {
        return null;
    }
}
