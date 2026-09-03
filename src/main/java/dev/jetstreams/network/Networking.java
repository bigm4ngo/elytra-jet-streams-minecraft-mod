package dev.jetstreams.network;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.field.WindField;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Common-side networking: registers the S2C physics sync payload and pushes the
 * authoritative physics config + derived field seed to players on join and after reloads.
 * Physics edits happen in the config file + {@code /jetstreams reload} by an operator -
 * there is deliberately no client-driven physics channel (since v1.2.0).
 * Client-side reception lives in the client source set ({@code ClientNetworking}).
 */
public final class Networking {
    private Networking() {}

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(SyncPhysicsPayload.TYPE, SyncPhysicsPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendTo(handler.player));
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
