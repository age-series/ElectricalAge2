package cam72cam.mod;

import cam72cam.mod.config.ConfigFile;

@ConfigFile.Comment("Configuration File")
@ConfigFile.Name("general")
@ConfigFile.File("universalmodcore.cfg")
public class Config {
    @ConfigFile.Comment("Size of each sprite in the texture sheet")
    public static int SpriteSize = 128;

    @ConfigFile.Comment("Enable Debug Logging")
    public static boolean DebugLogging = false;
}
