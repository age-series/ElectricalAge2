package cam72cam.mod.util;

import cam72cam.mod.ModCore;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.text.Command;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ModCoreCommand extends Command {
    @Override
    public String getPrefix() {
        return ModCore.MODID;
    }

    @Override
    public String getUsage() {
        return "Usage: " + ModCore.MODID + " entity list";
    }

    @Override
    public boolean opRequired() {
        return true;
    }

    @Override
    public boolean execute(World world, Consumer<PlayerMessage> sender, String[] args) {
        if (args.length == 2 && "entity".equals(args[0]) && "list".equals(args[1])) {
            Map<String, Integer> counts = new HashMap<>();
            for (Entity entity : world.getEntities(Entity.class)) {
                String id = entity.internal.getName();
                if (!counts.containsKey(id)) {
                    counts.put(id, 0);
                }
                counts.put(id, counts.get(id) + 1);
            }

            counts.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> sender.accept(PlayerMessage.direct(entry.getValue() + " x " + entry.getKey())));

        }
        return false;
    }
}
