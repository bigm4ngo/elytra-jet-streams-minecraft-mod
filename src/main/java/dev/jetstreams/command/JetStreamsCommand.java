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

/** {@code /jetstreams info|locate|reload}. */
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
                .then(Commands.literal("locate")
                        .requires(src -> src.permissions().hasPermission(
                                net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                        .then(Commands.literal("north").executes(ctx ->
                                locate(ctx.getSource(), dev.jetstreams.registry.JetFlowOption.NORTH)))
                        .then(Commands.literal("south").executes(ctx ->
                                locate(ctx.getSource(), dev.jetstreams.registry.JetFlowOption.SOUTH)))
                        .then(Commands.literal("east").executes(ctx ->
                                locate(ctx.getSource(), dev.jetstreams.registry.JetFlowOption.EAST)))
                        .then(Commands.literal("west").executes(ctx ->
                                locate(ctx.getSource(), dev.jetstreams.registry.JetFlowOption.WEST))))
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

    /**
     * Finds the nearest point inside a stream flowing toward the requested cardinal
     * direction and reports it (with a click-to-teleport suggestion).
     */
    private static int locate(CommandSourceStack source, int flowDir)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var level = player.level();
        var p = PhysicsResolver.activeFor(level);
        int family = flowDir == dev.jetstreams.registry.JetFlowOption.EAST
                || flowDir == dev.jetstreams.registry.JetFlowOption.WEST ? 1 : 0;
        int sign = flowDir == dev.jetstreams.registry.JetFlowOption.SOUTH
                || flowDir == dev.jetstreams.registry.JetFlowOption.EAST ? 1 : -1;
        String dirName = switch (flowDir) {
            case dev.jetstreams.registry.JetFlowOption.NORTH -> "northbound";
            case dev.jetstreams.registry.JetFlowOption.SOUTH -> "southbound";
            case dev.jetstreams.registry.JetFlowOption.EAST -> "eastbound";
            default -> "westbound";
        };
        String dirArrow = switch (flowDir) {
            case dev.jetstreams.registry.JetFlowOption.NORTH -> "(-Z)";
            case dev.jetstreams.registry.JetFlowOption.SOUTH -> "(+Z)";
            case dev.jetstreams.registry.JetFlowOption.EAST -> "(+X)";
            default -> "(-X)";
        };

        var field = WindField.field(WindField.fieldSeed(level, p), p);
        var hit = field.locateNearest(player.getX(), player.getY(), player.getZ(), family, sign, 24);
        if (hit == null) {
            source.sendFailure(Component.literal(
                    "§cNo " + dirName + " stream found in range (that is extremely unlikely - "
                            + "check the tunnel config)."));
            return 0;
        }
        String tp = String.format(Locale.ROOT, "/tp @s %.0f %.0f %.0f", hit.x(), hit.y(), hit.z());
        net.minecraft.network.chat.MutableComponent msg = Component.literal(String.format(Locale.ROOT,
                        "§bNearest %s stream %s: §fX=%.0f  Y=%.0f  Z=%.0f §7(%.0f blocks away, "
                                + "%d wide, %d tall, %d long) ",
                        dirName, dirArrow, hit.x(), hit.y(), hit.z(), hit.distance(),
                        (int) (hit.halfW() * 2), (int) (hit.halfH() * 2), (int) (hit.halfL() * 2)))
                .withStyle(style -> style.withClickEvent(
                        new net.minecraft.network.chat.ClickEvent.SuggestCommand(tp)));
        source.sendSuccess(() -> msg.append(Component.literal("§9§n[Teleport]")), false);
        return 1;
    }

    private static int info(ServerPlayer player) {
        var level = player.level();
        var p = PhysicsResolver.activeFor(level);
        FlightState st = FlightStateTracker.get(level, player.getUUID());
        double y = player.getY();
        double mult = WindField.fireworkMultiplier(p, y);
        double cruise = WindField.cruiseSpeed(p, y);
        double neutral = WindField.neutralCruiseSpeed(p, y);
        var s = WindField.sample(level, player.getX(), y, player.getZ(), p);
        var hit = WindField.tunnelHit(level, player.getX(), y, player.getZ(), p);

        String region = switch (s.region()) {
            case NS_STREAM -> "N/S tunnel";
            case EW_STREAM -> "E/W tunnel";
            case CROSSING -> "crossing (locked: " + (st.dominantFamily == 0 ? "N/S" : "E/W") + ")";
            case DEAD_ZONE -> "neutral zone";
        };
        double speedBps = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z) * 20.0;

        player.sendSystemMessage(Component.literal("§9Elytra Jet Streams§r — y=" + String.format(Locale.ROOT, "%.0f", y)));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " region: %s, profile=%.2f", region, st.windProfile)));
        if (hit != null) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    " tunnel: %dx%d blocks, %d long, axis y=%.0f, %.0f%% through",
                    (int) (hit.halfW() * 2), (int) (hit.halfH() * 2), (int) (hit.halfL() * 2),
                    hit.yCenter(), hit.u01() * 100.0)));
        }
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " speed: %.1f b/s | rocket x%.2f | tunnel cruise: %.1f b/s | neutral cruise: %.1f b/s (from y=%.0f)",
                speedBps, mult, cruise, neutral, p.neutralCruiseStartAltitude)));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " state: cruising=%s headwind=%s hypersonic=%s (≥%.0f b/s) turn=%.1f°/t",
                st.cruising, st.headwind, st.hypersonic, p.hypersonicSpeed, st.turnRate)));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                " chunks: corridor preload=%s tickets/tick=%d", p.chunkPreloadEnabled, p.preloadTicketsPerTick)));
        return 1;
    }
}
