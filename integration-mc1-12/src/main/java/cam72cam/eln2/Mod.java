package cam72cam.eln2;

import cam72cam.mod.ModCore;

@net.minecraftforge.fml.common.Mod(modid = Mod.MODID, name = Mod.NAME, version = Mod.VERSION, dependencies = "required-before:universalmodcore", acceptedMinecraftVersions = "[1.12,1.13)")
public class Mod {
    public static final String MODID = "eln2";
    public static final String NAME = "Electrical Age 2";
    public static final String VERSION = "0.1.0";

    static {
        try {
            Class<ModCore.Mod> cls = (Class<ModCore.Mod>) Class.forName("org.eln2.mc.ElnMain");
            ModCore.register(() -> {
                try {
                    return cls.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Could not construct mod " + MODID, e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Could not load mod " + MODID, e);
        }
    }
}
