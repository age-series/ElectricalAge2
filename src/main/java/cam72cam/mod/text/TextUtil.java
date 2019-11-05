package cam72cam.mod.text;

import net.minecraft.client.resources.I18n;

@SuppressWarnings("deprecation")
public class TextUtil {
    //TODO this breaks server side ...
    public static String translate(String name) {
        return I18n.format(name);
    }

    public static String translate(String name, Object[] objects) {
        return I18n.format(name, objects);
    }
}
