package cam72cam.mod.sound;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.ModCore;
import cam72cam.mod.entity.Player;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.world.World;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class Audio {
    @SideOnly(Side.CLIENT)
    private static ModSoundManager soundManager;

    public static void registerClientCallbacks() {
        ClientEvents.TICK.register(() -> {
            Player player = MinecraftClient.getPlayer();
            World world = null;
            if (player != null) {
                world = player.getWorld();
                soundManager.tick();
            }

            if (world == null && soundManager != null && soundManager.hasSounds()) {
                soundManager.stop();
            }
        });

        ClientEvents.SOUND_LOAD.register(event -> {
            if (soundManager == null) {
                soundManager = new ModSoundManager(event.getManager());
            } else {
                soundManager.handleReload(false);
            }
        });

        CommonEvents.World.LOAD.register(world -> {
            soundManager.handleReload(true);
        });

        CommonEvents.World.UNLOAD.register(world -> {
            soundManager.stop();
        });
    }

    public static void playSound(Vec3d pos, StandardSound sound, SoundCategory category, float volume, float pitch) {
        MinecraftClient.getPlayer().getWorld().internal.playSound(pos.x, pos.y, pos.z, sound.event, category.category, volume, pitch, false);
    }

    public static void playSound(Vec3i pos, StandardSound sound, SoundCategory category, float volume, float pitch) {
        playSound(new Vec3d(pos), sound, category, volume, pitch);
    }

    public static ISound newSound(Identifier oggLocation, boolean repeats, float attenuationDistance, float scale) {
        return soundManager.createSound(oggLocation, repeats, attenuationDistance, scale);
    }
}
