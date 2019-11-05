package cam72cam.mod.gui.helpers;

import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.resource.Identifier;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class GUIHelpers {
    public static final ResourceLocation CHEST_GUI_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    public static void drawRect(double x, double y, double width, double height, int color) {
        double zLevel = 0;

        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;
        GL11.glColor4f(f, f1, f2, f3);

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        bufferbuilder.pos(x + 0, y + height, zLevel).endVertex();
        bufferbuilder.pos(x + width, y + height, zLevel).endVertex();
        bufferbuilder.pos(x + width, y + 0, zLevel).endVertex();
        bufferbuilder.pos(x + 0, y + 0, zLevel).endVertex();
        tessellator.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void texturedRect(double x, double y, double width, double height) {
        double zLevel = 0;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bufferbuilder.pos(x + 0, y + height, zLevel).tex(0, 1).endVertex();
        bufferbuilder.pos(x + width, y + height, zLevel).tex(1, 1).endVertex();
        bufferbuilder.pos(x + width, y + 0, zLevel).tex(1, 0).endVertex();
        bufferbuilder.pos(x + 0, y + 0, zLevel).tex(0, 0).endVertex();
        tessellator.draw();
    }

    public static void drawFluid(Fluid fluid, double x, double d, double width, int height, int scale) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureMap().getAtlasSprite(fluid.internal.getAttributes().getStillTexture().toString());
        drawSprite(sprite, fluid.internal.getAttributes().getColor(), x, d, width, height, scale);
    }

    public static void drawSprite(TextureAtlasSprite sprite, int col, double x, double y, double width, double height, int scale) {
        double zLevel = 0;

        Minecraft.getInstance().getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.color4f((col >> 16 & 255) / 255.0f, (col >> 8 & 255) / 255.0f, (col & 255) / 255.0f, 1);
        int iW = sprite.getWidth() * scale;
        int iH = sprite.getHeight() * scale;

        float minU = sprite.getMinU();
        float minV = sprite.getMinV();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        for (int offY = 0; offY < height; offY += iH) {
            double curHeight = Math.min(iH, height - offY);
            float maxVScaled = sprite.getInterpolatedV(16.0 * curHeight / iH);
            for (int offX = 0; offX < width; offX += iW) {
                double curWidth = Math.min(iW, width - offX);
                float maxUScaled = sprite.getInterpolatedU(16.0 * curWidth / iW);
                buffer.pos(x + offX, y + offY, zLevel).tex(minU, minV).endVertex();
                buffer.pos(x + offX, y + offY + curHeight, zLevel).tex(minU, maxVScaled).endVertex();
                buffer.pos(x + offX + curWidth, y + offY + curHeight, zLevel).tex(maxUScaled, maxVScaled).endVertex();
                buffer.pos(x + offX + curWidth, y + offY, zLevel).tex(maxUScaled, minV).endVertex();
            }
        }
        tessellator.draw();

        Minecraft.getInstance().getTextureManager().bindTexture(CHEST_GUI_TEXTURE);
    }

    public static void drawTankBlock(double x, double y, double width, double height, Fluid fluid, float percentFull) {
        drawTankBlock(x, y, width, height, fluid, percentFull, true, 0x00000000);
    }

    public static void drawTankBlock(double x, double y, double width, double height, Fluid fluid, float percentFull, boolean drawBackground, int color) {
        if (drawBackground) {
            drawRect(x, y, width, height, 0xFF000000);
        }

        if (percentFull > 0 && fluid != null) {
            int fullHeight = Math.max(1, (int) (height * percentFull));
            drawFluid(fluid, x, y + height - fullHeight, width, fullHeight, 2);
            drawRect(x, y + height - fullHeight, width, fullHeight, color);
        }
        GlStateManager.color4f(1, 1, 1, 1);
    }

    public static void drawCenteredString(String text, int x, int y, int color) {
        Minecraft.getInstance().fontRenderer.drawStringWithShadow(text, (float) (x - Minecraft.getInstance().fontRenderer.getStringWidth(text) / 2), (float) y, color);
    }

    public static void bindTexture(Identifier tex) {
        Minecraft.getInstance().getTextureManager().bindTexture(tex.internal);
    }

    public static int getScreenWidth() {
        return Minecraft.getInstance().mainWindow.getFramebufferWidth();
    }

    public static int getScreenHeight() {
        return Minecraft.getInstance().mainWindow.getFramebufferHeight();
    }

    public static void drawItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderItemIntoGUI(stack.internal, x, y);
    }
}
