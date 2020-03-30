package #PACKAGE#;

import cam72cam.mod.ModCore;

@net.minecraftforge.fml.common.Mod(modid = Mod.MODID, name = "#NAME#", version = "#VERSION#", dependencies = "required-before:universalmodcore@[#UMC_API#,#UMC_API_NEXT#)", acceptedMinecraftVersions = "[1.12,1.13)")
public class Mod {
    public static final String MODID = "#ID#";

    static {
        try {
            Class<ModCore.Mod> cls = (Class<ModCore.Mod>) Class.forName("#PACKAGE#.#CLASS#");
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

