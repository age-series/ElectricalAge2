package cam72cam.#MODID#;

import cam72cam.mod.ModCore;

@net.minecraftforge.fml.common.Mod(Mod.MODID)
public class Mod {
    public static final String MODID = "#MODID#";
    public static final String NAME = "#MODNAME#";
    public static final String VERSION = "#MODVERSION#";

    static {
        try {
            Class<ModCore.Mod> cls = (Class<ModCore.Mod>) Class.forName("#MODCLASS#");
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
    public static void hackRegistration() {
        System.out.println("[" + NAME + "] Hello UniversalModCore");
    }
}

