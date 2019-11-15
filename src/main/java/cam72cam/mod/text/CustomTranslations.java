package cam72cam.mod.text;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CustomTranslations {
    private static Map<String, Map<String, String>> translations = new HashMap<>();

    @OnlyIn(Dist.CLIENT)
    private static String clientLang() {
        try {
            return Minecraft.getInstance().getLanguageManager().getCurrentLanguage().getCode();
        } catch (NullPointerException ex) {
            return "en_us";
        }
    }
    private static String serverLang() {
        // Yes I know this sucks, it will take a bunch of refactoring to fix
        return "en_us";
    }

    private static String getLang() {
        return DistExecutor.runForDist(() -> CustomTranslations::clientLang, () -> CustomTranslations::serverLang);
    }

    public static Map<String, String> getTranslations() {
        return getTranslations(getLang());
    }

    public static Map<String, String> getTranslations(String lang) {
        if (!translations.containsKey(lang)) {
            try {
                String langStr = lang.split("_")[0] + "_" + lang.split("_")[1].toUpperCase();
                InputStream input = CustomTranslations.class.getResourceAsStream("/assets/immersiverailroading/lang/" + langStr + ".lang");
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));

                Map<String, String> lt = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] splits = line.split("=", 2);
                    if (splits.length == 2) {
                        lt.put(splits[0], splits[1]);
                    }
                }
                translations.put(lang, lt);
                reader.close();
                input.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                translations.put(lang, getTranslations("en_us"));
            }
        }
        return translations.get(lang);
    }


}
