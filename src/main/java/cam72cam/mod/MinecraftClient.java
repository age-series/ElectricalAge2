package cam72cam.mod;

import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.mojang.blaze3d.platform.GLX;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.RayTraceResult;

public class MinecraftClient {
    public static Player getPlayer() {
        if (Minecraft.getInstance().player == null) {
            return null;
        }
        return new Player(Minecraft.getInstance().player);
    }

    public static void startProfiler(String section) {
        Minecraft.getInstance().getProfiler().startSection(section);
    }

    public static void endProfiler() {
        Minecraft.getInstance().getProfiler().endSection();
    }

    public static boolean useVBO() {
        return GLX.useVbo();
    }

    public static Entity getEntityMouseOver() {
        net.minecraft.entity.Entity ent = Minecraft.getInstance().pointedEntity;
        if (ent != null) {
            return getPlayer().getWorld().getEntity(ent.getUniqueID(), Entity.class);
        }
        return null;
    }

    public static Vec3i getBlockMouseOver() {
        return Minecraft.getInstance().objectMouseOver.getType() == RayTraceResult.Type.BLOCK ? new Vec3i(new Vec3d(Minecraft.getInstance().objectMouseOver.getHitVec())) : null;
    }

    public static Vec3d getPosMouseOver() {
        return Minecraft.getInstance().objectMouseOver.getType() == RayTraceResult.Type.BLOCK ? new Vec3d(Minecraft.getInstance().objectMouseOver.getHitVec()) : null;
    }

    public static boolean isPaused() {
        return Minecraft.getInstance().isGamePaused();
    }
}
