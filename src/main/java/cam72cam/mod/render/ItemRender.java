package cam72cam.mod.render;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.gui.Progress;
import cam72cam.mod.item.ItemBase;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.world.World;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ItemRender {
    private static final List<BakedQuad> EMPTY = new ArrayList<>();
    private static final SpriteSheet iconSheet = new SpriteSheet(128);

    public static void register(ItemBase item, Identifier tex) {
        ClientEvents.MODEL_BAKE.subscribe(event -> event.getModelRegistry().putObject(new ModelResourceLocation(item.getRegistryName().internal, ""), new ItemLayerModel(ImmutableList.of(
                tex.internal
        )).bake(TRSRTransformation.identity(), DefaultVertexFormats.ITEM, ModelLoader.defaultTextureGetter())));

        ClientEvents.TEXTURE_STITCH.subscribe(() -> Minecraft.getMinecraft().getTextureMapBlocks().registerSprite(tex.internal));

        ClientEvents.MODEL_CREATE.subscribe(() -> ModelLoader.setCustomModelResourceLocation(item.internal, 0,
                new ModelResourceLocation(item.getRegistryName().internal, "")));
    }

    public static void register(ItemBase item, IItemModel model) {
        ClientEvents.MODEL_CREATE.subscribe(() ->
                ModelLoader.setCustomModelResourceLocation(item.internal, 0, new ModelResourceLocation(item.getRegistryName().internal, ""))
        );

        ClientEvents.MODEL_BAKE.subscribe((ModelBakeEvent event) -> event.getModelRegistry().putObject(new ModelResourceLocation(item.getRegistryName().internal, ""), new BakedItemModel(model)));

        if (model instanceof ISpriteItemModel) {
            ClientEvents.RELOAD.subscribe(() -> {
                List<ItemStack> variants = item.getItemVariants(null);
                Progress.Bar bar = Progress.push(item.getClass().getSimpleName() + " Icon", variants.size());
                for (ItemStack stack : variants) {
                    String id = ((ISpriteItemModel) model).getSpriteKey(stack);
                    bar.step(id);
                    createSprite(id, ((ISpriteItemModel) model).getSpriteModel(stack));
                }
                Progress.pop(bar);
            });
        }
    }

    public enum ItemRenderType {
        NONE(TransformType.NONE),
        THIRD_PERSON_LEFT_HAND(TransformType.THIRD_PERSON_LEFT_HAND),
        THIRD_PERSON_RIGHT_HAND(TransformType.THIRD_PERSON_RIGHT_HAND),
        FIRST_PERSON_LEFT_HAND(TransformType.FIRST_PERSON_LEFT_HAND),
        FIRST_PERSON_RIGHT_HAND(TransformType.FIRST_PERSON_RIGHT_HAND),
        HEAD(TransformType.HEAD),
        GUI(TransformType.GUI),
        ENTITY(TransformType.GROUND),
        FRAME(TransformType.FIXED);

        private final TransformType type;

        ItemRenderType(TransformType type) {
            this.type = type;
        }

        public static ItemRenderType from(TransformType cameraTransformType) {
            for (ItemRenderType type : values()) {
                if (cameraTransformType == type.type) {
                    return type;
                }
            }
            return null;
        }
    }

    @FunctionalInterface
    public interface IItemModel {
        StandardModel getModel(World world, ItemStack stack);
        default void applyTransform(ItemRenderType type) {
            defaultTransform(type);
        }
        static void defaultTransform(ItemRenderType type) {
            switch (type) {
                case FRAME:
                    GL11.glRotated(90, 0, 1, 0);
                    break;
                case HEAD:
                    GL11.glScaled(2, 2, 2);
                    GL11.glTranslated(0, 1, 0);
            }
        }
    }

    public interface ISpriteItemModel extends IItemModel {
        String getSpriteKey(ItemStack stack);
        StandardModel getSpriteModel(ItemStack stack);
    }


    private static void createSprite(String id, StandardModel model) {
        int width = iconSheet.spriteSize;
        int height = iconSheet.spriteSize;
        Framebuffer fb = new Framebuffer(width, height, true);
        fb.setFramebufferColor(0, 0, 0, 0);
        fb.framebufferClear();
        fb.bindFramebuffer(true);

        GLBoolTracker depth = new GLBoolTracker(GL11.GL_DEPTH_TEST, true);
        int oldDepth = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glClearDepth(1);

        model.renderCustom();

        ByteBuffer buff = ByteBuffer.allocateDirect(4 * width * height);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buff);

        fb.unbindFramebuffer();
        fb.deleteFramebuffer();
        GL11.glDepthFunc(oldDepth);
        depth.restore();

        iconSheet.setSprite(id, buff);
    }

    static class BakedItemModel implements IBakedModel {
        private ItemStack stack;
        private final IItemModel model;
        private ItemRenderType type;


        BakedItemModel(IItemModel model) {
            this.stack = null;
            this.model = model;
            this.type = ItemRenderType.NONE;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (stack == null) {
                return EMPTY;
            }

            if (type == ItemRenderType.GUI && model instanceof ISpriteItemModel) {
                iconSheet.renderSprite(((ISpriteItemModel) model).getSpriteKey(stack));
                return EMPTY;
            }

            StandardModel std = model.getModel(MinecraftClient.getPlayer().getWorld(), stack);
            if (std == null) {
                return EMPTY;
            }


            /*
             * I am an evil wizard!
             *
             * So it turns out that I can stick a draw call in here to
             * render my own stuff. This subverts forge's entire baked model
             * system with a single line of code and injects my own OpenGL
             * payload. Fuck you modeling restrictions.
             *
             * This is probably really fragile if someone calls getQuads
             * before actually setting up the correct GL context.
             */
            if (side == null) {
                model.applyTransform(type);
                std.renderCustom();
            }

            return std.getQuads(side, rand);
        }

        @Override
        public boolean isAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return null;
        }

        @Override
        public ItemOverrideList getOverrides() {
            return new ItemOverrideListHack();
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(TransformType cameraTransformType) {
            this.type = ItemRenderType.from(cameraTransformType);
            return ForgeHooksClient.handlePerspective(this, cameraTransformType);
        }

        class ItemOverrideListHack extends ItemOverrideList {
            ItemOverrideListHack() {
                super(new ArrayList<>());
            }

            @Override
            public IBakedModel handleItemState(IBakedModel originalModel, net.minecraft.item.ItemStack stack, @Nullable net.minecraft.world.World world, @Nullable EntityLivingBase entity) {
                BakedItemModel.this.stack = new ItemStack(stack);
                return BakedItemModel.this;
            }
        }
    }
}
