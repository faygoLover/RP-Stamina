package faygolover.rpstamina.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.capability.StaminaProvider;
import faygolover.rpstamina.core.StaminaManager;
import faygolover.rpstamina.data.DataLoaders;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Locale;

/** /stamina get|set|add|refill|reload|export_defaults|debug (нужны права оператора). */
@Mod.EventBusSubscriber(modid = RpStamina.MODID)
public final class StaminaCommand {
    private StaminaCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("stamina")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("get")
                        .executes(c -> get(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> get(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                                        .executes(c -> set(c.getSource(), EntityArgument.getPlayers(c, "players"),
                                                FloatArgumentType.getFloat(c, "amount"))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg())
                                        .executes(c -> add(c.getSource(), EntityArgument.getPlayers(c, "players"),
                                                FloatArgumentType.getFloat(c, "amount"))))))
                .then(Commands.literal("refill")
                        .executes(c -> refill(c.getSource(), java.util.List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> refill(c.getSource(), EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("reload").executes(c -> reload(c.getSource())))
                .then(Commands.literal("export_defaults").executes(c -> export(c.getSource())))
                .then(Commands.literal("debug")
                        .executes(c -> debug(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> debug(c.getSource(), EntityArgument.getPlayer(c, "player"))))));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static int get(CommandSourceStack src, ServerPlayer p) {
        PlayerStamina s = StaminaProvider.get(p);
        if (s == null || !StaminaManager.isActive(p)) {
            src.sendSuccess(() -> Component.translatable("commands.rpstamina.inactive", p.getName()), false);
            return 0;
        }
        Component suffix = s.exhausted ? Component.translatable("commands.rpstamina.exhausted") : Component.empty();
        src.sendSuccess(() -> Component.translatable("commands.rpstamina.get", p.getName(),
                fmt(s.stamina), fmt(StaminaManager.max(p)), suffix), false);
        return 1;
    }

    private static int set(CommandSourceStack src, Collection<ServerPlayer> players, float amount) {
        int n = 0;
        for (ServerPlayer p : players) {
            PlayerStamina s = StaminaProvider.get(p);
            if (s == null) continue;
            StaminaManager.setDirect(p, s, amount);
            n++;
            src.sendSuccess(() -> Component.translatable("commands.rpstamina.set", p.getName(), fmt(amount)), true);
        }
        return n;
    }

    private static int add(CommandSourceStack src, Collection<ServerPlayer> players, float amount) {
        int n = 0;
        for (ServerPlayer p : players) {
            PlayerStamina s = StaminaProvider.get(p);
            if (s == null) continue;
            StaminaManager.addDirect(p, s, amount);
            n++;
            src.sendSuccess(() -> Component.translatable("commands.rpstamina.add", p.getName(), fmt(amount)), true);
        }
        return n;
    }

    private static int refill(CommandSourceStack src, Collection<ServerPlayer> players) {
        int n = 0;
        for (ServerPlayer p : players) {
            PlayerStamina s = StaminaProvider.get(p);
            if (s == null) continue;
            StaminaManager.setDirect(p, s, StaminaManager.max(p));
            s.exhausted = false;
            n++;
        }
        int count = n;
        src.sendSuccess(() -> Component.translatable("commands.rpstamina.refill", count), true);
        return n;
    }

    private static int reload(CommandSourceStack src) {
        DataLoaders.reloadCustom();
        src.sendSuccess(() -> Component.translatable("commands.rpstamina.reload"), true);
        return 1;
    }

    private static int export(CommandSourceStack src) {
        int n = DataLoaders.exportDefaults();
        String dir = DataLoaders.customDir().toString();
        src.sendSuccess(() -> Component.translatable("commands.rpstamina.export", n, dir), true);
        return n;
    }

    private static int debug(CommandSourceStack src, ServerPlayer p) {
        PlayerStamina s = StaminaProvider.get(p);
        if (s == null || !StaminaManager.isActive(p)) {
            src.sendSuccess(() -> Component.translatable("commands.rpstamina.inactive", p.getName()), false);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("commands.rpstamina.debug", p.getName(),
                fmt(s.stamina), fmt(s.max), fmt3(s.regenRate), s.delay, s.exhausted, s.slowTicks,
                fmt3(StaminaManager.burdenMultiplier(p, s)), s.crashTicks > 0 ? fmt3(s.crashMult) + "/" + s.crashTicks : "-",
                s.pools.size(), s.passives.size()), false);
        return 1;
    }
}
