package dev.jetstreams.network;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.field.WindField;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;

/**
 * Common-side networking: registers the S2C physics sync + C2S physics update payloads,
 * pushes the authoritative physics config + derived field seed to players on join and
 * after reloads, and applies operator-submitted physics updates with permission checks.
 * Client-side reception lives in the client source set ({@code ClientNetworking}).
 */
public final class Networking {
    private Networking() {}

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(SyncPhysicsPayload.TYPE, SyncPhysicsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdatePhysicsPayload.TYPE, UpdatePhysicsPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendTo(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(UpdatePhysicsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> handlePhysicsUpdate(server, player, payload.json()));
        });
    }

    private static void handlePhysicsUpdate(MinecraftServer server, ServerPlayer player, String json) {
        boolean allowed = server.isSingleplayerOwner(player.nameAndId())
                || player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
        if (!allowed) {
            player.sendSystemMessage(Component.literal(
                    "§c[Elytra Jet Streams] Only server operators can change physics settings."));
            return;
        }
        if (ConfigManager.applyPhysicsJson(json)) {
            WindField.invalidate();
            broadcastAll(server);
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§b[Elytra Jet Streams] §fPhysics settings updated by §e" + player.nameAndId().name()
                            + "§f."), false);
        } else {
            player.sendSystemMessage(Component.literal(
                    "§c[Elytra Jet Streams] Could not apply settings: malformed config."));
        }
    }

    /** (Re)sends the authoritative config + field seed to one player. */
    public static void sendTo(ServerPlayer player) {
        Level level = player.level();
        long seed = WindField.fieldSeed(level, ConfigManager.get().physics);
        ServerPlayNetworking.send(player, SyncPhysicsPayload.of(seed));
    }

    /** Broadcasts after a config reload. */
    public static void broadcastAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }
}
