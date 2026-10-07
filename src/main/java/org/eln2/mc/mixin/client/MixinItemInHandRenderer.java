package org.eln2.mc.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.eln2.mc.common.content.FlashlightHandAnchors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void eln2$captureFlashlightAnchor(
        LivingEntity pEntity,
        ItemStack pItemStack,
        ItemDisplayContext pDisplayContext,
        boolean pLeftHand,
        PoseStack pPoseStack,
        MultiBufferSource pBuffer,
        int pSeed,
        CallbackInfo ci
    ) {
        FlashlightHandAnchors.INSTANCE.capture(pEntity, pItemStack, pDisplayContext, pLeftHand, pPoseStack);
    }
}
