package cam72cam.mod.render;

import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.Direction;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class StandardModel {
    private List<Pair<BlockState, IBakedModel>> models = new ArrayList<>();
    private List<Consumer<Float>> custom = new ArrayList<>();

    private static BlockState itemToBlockState(cam72cam.mod.item.ItemStack stack) {
        Block block = Block.getBlockFromItem(stack.internal.getItem());
        BlockState gravelState = block.getDefaultState();//.getStateFromMeta(stack.internal.getMetadata());
        if (block instanceof LogBlock) {
            gravelState = gravelState.with(LogBlock.AXIS, Direction.Axis.Z);
        }
        return gravelState;
    }

    public StandardModel addColorBlock(Color color, Vec3d translate, Vec3d scale) {
        BlockState state = Fuzzy.CONCRETE.enumerate()
                .stream()
                .map(x -> Block.getBlockFromItem(x.internal.getItem()))
                .filter(x -> x.getMaterialColor(null, null, null) == color.internal.getMapColor())
                .map(Block::getDefaultState)
                .findFirst().get();

        IBakedModel model = Minecraft.getInstance().getBlockRendererDispatcher().getBlockModelShapes().getModel(state);
        models.add(Pair.of(state, new BakedScaledModel(model, scale, translate)));
        return this;
    }

    public StandardModel addSnow(int layers, Vec3d translate) {
        layers = Math.max(1, Math.min(8, layers));
        BlockState state = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, layers);
        IBakedModel model = Minecraft.getInstance().getBlockRendererDispatcher().getBlockModelShapes().getModel(state);
        models.add(Pair.of(state, new BakedScaledModel(model, new Vec3d(1, 1, 1), translate)));
        return this;
    }

    public StandardModel addItemBlock(ItemStack bed, Vec3d translate, Vec3d scale) {
        BlockState state = itemToBlockState(bed);
        IBakedModel model = Minecraft.getInstance().getBlockRendererDispatcher().getBlockModelShapes().getModel(state);
        models.add(Pair.of(state, new BakedScaledModel(model, scale, translate)));
        return this;
    }

    public StandardModel addItem(ItemStack stack, Vec3d translate, Vec3d scale) {
        custom.add((pt) -> {
            GL11.glPushMatrix();
            {
                GL11.glTranslated(translate.x, translate.y, translate.z);
                GL11.glScaled(scale.x, scale.y, scale.z);
                Minecraft.getInstance().getItemRenderer().renderItem(stack.internal, ItemCameraTransforms.TransformType.NONE);
            }
            GL11.glPopMatrix();
        });
        return this;
    }

    public StandardModel addCustom(Runnable fn) {
        this.custom.add(pt -> fn.run());
        return this;
    }

    public StandardModel addCustom(Consumer<Float> fn) {
        this.custom.add(fn);
        return this;
    }

    List<BakedQuad> getQuads(Direction side, Random rand) {
        List<BakedQuad> quads = new ArrayList<>();
        for (Pair<BlockState, IBakedModel> model : models) {
            quads.addAll(model.getValue().getQuads(model.getKey(), side, rand));
        }

        return quads;
    }

    public void render() {
        render(0);
    }

    public void render(float partialTicks) {
        renderCustom(partialTicks);
        renderQuads();
    }

    public void renderQuads() {
        List<BakedQuad> quads = new ArrayList<>();
        for (Pair<BlockState, IBakedModel> model : models) {
            quads.addAll(model.getRight().getQuads(null, null, new Random()));
            for (Direction facing : Direction.values()) {
                quads.addAll(model.getRight().getQuads(null, facing, new Random()));
            }

        }
        if (quads.isEmpty()) {
            return;
        }

        Minecraft.getInstance().getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);

        BufferBuilder worldRenderer = new BufferBuilder(2048);
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        quads.forEach(quad -> LightUtil.renderQuadColor(worldRenderer, quad, -1));
        worldRenderer.finishDrawing();
        new WorldVertexBufferUploader().draw(worldRenderer);
    }

    public void renderCustom() {
        renderCustom(0);
    }

    public void renderCustom(float partialTicks) {
        custom.forEach(cons -> cons.accept(partialTicks));
    }

    public boolean hasCustom() {
        return !custom.isEmpty();
    }
}
