package cam72cam.mod.text;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeHooks;

public class PlayerMessage {
    public final ITextComponent internal;

    private PlayerMessage(ITextComponent component) {
        internal = component;
    }

    public static PlayerMessage direct(String msg) {
        return new PlayerMessage(new StringTextComponent(msg));
    }

    public static PlayerMessage translate(String msg, Object... objects) {
        return new PlayerMessage(new TranslationTextComponent(msg, objects));
    }

    public static PlayerMessage url(String url) {
        return new PlayerMessage(ForgeHooks.newChatWithLinks(url));
    }
}
