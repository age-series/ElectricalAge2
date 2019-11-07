package cam72cam.mod.event;

import cam72cam.mod.ModCore;
import cam72cam.mod.entity.EntityRegistry;
import cam72cam.mod.input.Keyboard;
import cam72cam.mod.input.Mouse;
import cam72cam.mod.render.EntityRenderer;
import cam72cam.mod.render.GlobalRender;
import cam72cam.mod.sound.Audio;
import cam72cam.mod.util.Hand;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Consumer;
import java.util.function.Function;

public class ClientEvents {

    private static void registerClientEvents() {
        EntityRegistry.registerClientEvents();
        EntityRenderer.registerClientEvents();
        Mouse.registerClientEvents();
        Keyboard.registerClientEvents();
        GlobalRender.registerClientEvents();
        Audio.registerClientCallbacks();
    }

    public static void fireReload() {
        RELOAD.execute(Runnable::run);
    }

    public static final Event<Runnable> TICK = new Event<>();
    public static final Event<Function<Hand, Boolean>> CLICK = new Event<>();
    public static final Event<Runnable> MODEL_CREATE = new Event<>();
    public static final Event<Consumer<ModelBakeEvent>> MODEL_BAKE = new Event<>();
    public static final Event<Consumer<TextureStitchEvent.Pre>> TEXTURE_STITCH = new Event<>();
    public static final Event<Runnable> REGISTER_ENTITY = new Event<>();
    public static final Event<Consumer<RenderGameOverlayEvent.Text>> RENDER_DEBUG = new Event<>();
    public static final Event<Consumer<RenderGameOverlayEvent.Pre>> RENDER_OVERLAY = new Event<>();
    public static final Event<Consumer<Float>> RENDER_MOUSEOVER = new Event<>();
    public static final Event<Consumer<SoundLoadEvent>> SOUND_LOAD = new Event<>();
    public static final Event<Runnable> RELOAD = new Event<>();

    @Mod.EventBusSubscriber(modid = ModCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientEventBusForge {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            TICK.execute(Runnable::run);
        }

        @SubscribeEvent
        public static void onClick(InputEvent.MouseInputEvent event) {
            int attackID = Minecraft.getInstance().gameSettings.keyBindAttack.getKey().getKeyCode();
            int useID = Minecraft.getInstance().gameSettings.keyBindUseItem.getKey().getKeyCode();

            if ((event.getButton() == attackID || event.getButton() == useID) && event.getAction() == 1) {
                Hand button = attackID == event.getButton() ? Hand.SECONDARY : Hand.PRIMARY;
                if (!CLICK.executeCancellable(x -> x.apply(button))) {
                    //event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public static void onDebugRender(RenderGameOverlayEvent.Text event) {
            RENDER_DEBUG.execute(x -> x.accept(event));
        }

        @SubscribeEvent
        public static void onOverlayEvent(RenderGameOverlayEvent.Pre event) {
            RENDER_OVERLAY.execute(x -> x.accept(event));
        }

        @SubscribeEvent
        public static void onRenderMouseover(DrawBlockHighlightEvent event) {
            RENDER_MOUSEOVER.execute(x -> x.accept(event.getPartialTicks()));
        }

        @SubscribeEvent
        public static void onSoundLoad(SoundLoadEvent event) {
            SOUND_LOAD.execute(x -> x.accept(event));
        }
    }

    @Mod.EventBusSubscriber(modid = ModCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEventBusMod {
        static {
            registerClientEvents();
        }

        @SubscribeEvent
        public static void registerModels(ModelRegistryEvent event) {
            MODEL_CREATE.execute(Runnable::run);
        }

        @SubscribeEvent
        public static void onModelBakeEvent(ModelBakeEvent event) {
            MODEL_BAKE.execute(x -> x.accept(event));
        }

        @SubscribeEvent
        public static void onTextureStitchEvent(TextureStitchEvent.Pre event) {
            TEXTURE_STITCH.execute(x -> x.accept(event));
        }

        @SubscribeEvent
        public static void registerEntities(RegistryEvent.Register<EntityType<?>> event) {
            REGISTER_ENTITY.execute(Runnable::run);
        }
    }
}
