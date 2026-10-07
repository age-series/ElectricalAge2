package org.eln2.mc.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.server.level.BlockDestructionProgress;
import org.eln2.mc.client.render.DrillAreaBreakRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Final
    @Shadow(aliases = {"f_109409_"})
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            ordinal = 2
        )
    )
    private void eln2$renderDrillAreaBreak(
        PoseStack pPoseStack,
        float pPartialTick,
        long pFinishNanoTime,
        boolean pRenderBlockOutline,
        Camera pCamera,
        GameRenderer pGameRenderer,
        LightTexture pLightTexture,
        org.joml.Matrix4f pProjectionMatrix,
        CallbackInfo ci
    ) {
        net.minecraft.world.phys.Vec3 cam = pCamera.getPosition();
        DrillAreaBreakRenderer.INSTANCE.render(
            destructionProgress,
            pPoseStack,
            cam.x,
            cam.y,
            cam.z
        );
    }
}
