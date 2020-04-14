package cam72cam.mod.text;

import cam72cam.mod.world.World;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.*;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class Command {
    private static final List<Command> commands = new ArrayList<>();

    public static void register(Command cmd) {
        commands.add(cmd);
    }

    public static void registration(CommandDispatcher<CommandSource> ch) {
        /* TODO
        for (Command command : commands) {
            ch.register(LiteralArgumentBuilder.literal(command.getPrefix()).executes((ctx) -> {
                boolean ok = command.execute(World.get(ctx.getSource().getEntity().getEntityWorld()), msg -> ctx.getSource().getEntity().sendMessage(msg.internal), ctx.getInput().split(" "));
                if (!ok) {
                    ctx.getSource().getEntity().sendMessage(new StringTextComponent(command.getUsage()));
                }
                return ok ? 1 : -1;
            }).requires((ctx) -> {
                    try {
                        return ctx.getSource().asPlayer().hasPermissionLevel(command.opRequired() ? 4 : 0);
                    } catch (CommandSyntaxException e) {
                        return false;
                    }
                })
            );
        }
        */
    }

    public abstract String getPrefix();

    public abstract String getUsage();

    public abstract boolean opRequired();

    public abstract boolean execute(World world, Consumer<PlayerMessage> sender, String[] args);
}
