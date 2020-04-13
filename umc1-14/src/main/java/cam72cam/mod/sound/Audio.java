package cam72cam.mod.sound;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Player;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class Audio {
    @OnlyIn(Dist.CLIENT)
    private static ModSoundManager soundManager;

    @OnlyIn(Dist.CLIENT)
    public static void registerClientCallbacks() {
        ClientEvents.TICK.subscribe(() -> {
            if (soundManager == null) {
                soundManager = new ModSoundManager();
            }

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

        ClientEvents.SOUND_LOAD.subscribe(event -> {
            if (soundManager == null) {
                soundManager = new ModSoundManager();
            } else {
                soundManager.handleReload(false);
            }
        });

        CommonEvents.World.LOAD.subscribe(world -> soundManager.handleReload(true));

        CommonEvents.World.UNLOAD.subscribe(world -> soundManager.stop());
    }

    public static void playSound(World world, Vec3d pos, StandardSound sound, SoundCategory category, float volume, float pitch) {
        world.internal.playSound(pos.x, pos.y, pos.z, sound.event, category.category, volume, pitch, false);
    }

    public static void playSound(World world, Vec3i pos, StandardSound sound, SoundCategory category, float volume, float pitch) {
        playSound(world, new Vec3d(pos), sound, category, volume, pitch);
    }

    public static ISound newSound(Identifier oggLocation, boolean repeats, float attenuationDistance, float scale) {
        return soundManager.createSound(oggLocation, repeats, attenuationDistance, scale);
    }

    public static void setSoundChannels(int max) {
        //SoundSystemConfig.setNumberNormalChannels(Math.max(SoundSystemConfig.getNumberNormalChannels(), max));
    }
}
