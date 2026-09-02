package dev.jetstreams.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.field.WindField;
import dev.jetstreams.network.Networking;
import dev.jetstreams.physics.FlightState;
import dev.jetstreams.physics.FlightStateTracker;
import dev.jetstreams.physics.PhysicsResolver;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** {@code /jetstreams info} and {@code /jetstreams reload}. */
public final class JetStreamsCommand {
    private JetStreamsCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jetstreams")
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return info(player);
                }))
                .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(
                                net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                        .executes(ctx -> {
                            ConfigManager.load();
                            WindField.invalidate();
                            Networking.broadcastAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Elytra Jet Streams config reloaded"), true);
                            return 1;
                        }))
        );
    }

    private static int info(ServerPlayer player) {
        var level = player.level();
        var p = PhysicsResolver.activeFor(level);
        FlightState st = FlightStateTracker.get(level, player.getUUID());
        double y = player.getY();
        double mult = WindField.fireworkMultiplier(p, y);
        double cruise = WindField.cruiseSpeed(p, y);
        var s = WindField.sample(level, player.getX(), player.getZ(), p);

        String region = switch (s.region()) {
            case NS_STREAM -> "N/S stream";
            case EW_STREAM -> "E/W stream";
            case CROSSING -> "crossing (locked: " + (st.dominantFamily == 0 ? "N/S" : "E/W") + ")";
            case DEAD_ZONE -> "dead zone";
        };
        double speedBps = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z) * 20.0;

        player.sendSystemMessage(Component.literal("§9Elytra Jet Streams§r — y=" + String.format(Locale.ROOT, "%.0f", y)));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " region: %s, profile=%.2f", region, st.windProfile)));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " speed: %.1f b/s | rocket x%.2f | cruise target: %.1f b/s",
                speedBps, mult, cruise)));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " state: cruising=%s headwind=%s hypersonic=%s (≥%.0f b/s) turn=%.1f°/t",
                st.cruising, st.headwind, st.hypersonic, p.hypersonicSpeed, st.turnRate)));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " chunks: corridor preload=%s tickets/tick=%d", p.chunkPreloadEnabled, p.preloadTicketsPerTick)));
        return 1;
    }
}
