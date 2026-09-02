package dev.jetstreams.chunk;

import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.field.WindField;
import dev.jetstreams.physics.FlightState;
import dev.jetstreams.physics.FlightStateTracker;
import dev.jetstreams.physics.PhysicsResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Predictive corridor pre-loading for high-speed elytra travel.
 *
 * <p>While a player glides above the activation altitude, chunks along the predicted path
 * (linear extrapolation of velocity) are requested a few per tick via expiring-free tickets,
 * spread the world-gen cost over time instead of spike-loading on arrival.
 *
 * <p><b>Hypersonic freeze:</b> at/above {@code hypersonicSpeed} no <i>new</i> chunks are
 * requested - the player rides exclusively on what was pre-loaded while accelerating.
 *
 * <p>The ticket type has no timeout of its own; this class removes tickets once they are
 * {@code preloadExpiryTicks} old or their owner disconnects, so nothing leaks.
 */
public final class FlightChunkPreloader {
    /** LOADING-only ticket (no persistence, no simulation): exactly what render-ready chunks need. */
    public static TicketType JETSTREAM_CORRIDOR;

    private static final Map<UUID, PerPlayer> STATE = new ConcurrentHashMap<>();

    private static final class PerPlayer {
        final Map<Long, Long> ticketed = new HashMap<>(); // packed chunk pos -> game tick added
        ResourceKey<net.minecraft.world.level.Level> worldKey;
    }

    private FlightChunkPreloader() {}

    public static void register() {
        JETSTREAM_CORRIDOR = Registry.register(
                BuiltInRegistries.TICKET_TYPE,
                dev.jetstreams.JetStreams.id("corridor"),
                new TicketType(TicketType.NO_TIMEOUT, 2));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(FlightChunkPreloader::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PerPlayer pp = STATE.remove(handler.player.getUUID());
            if (pp != null && pp.worldKey != null) {
                ServerLevel level = server.getLevel(pp.worldKey);
                if (level != null) {
                    releaseAll(level, pp);
                }
            }
        });
    }

    private static void tick(ServerLevel level) {
        JetStreamsConfig.Physics p = PhysicsResolver.activeFor(level);
        if (!p.chunkPreloadEnabled || !WindField.enabledFor(level, p)) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            FlightState st = FlightStateTracker.get(level, player.getUUID());
            PerPlayer pp = STATE.computeIfAbsent(player.getUUID(), id -> new PerPlayer());

            // Always expire stale tickets first, even mid-flight.
            if (expireStale(level, pp, now, p)) {
                continue;
            }

            if (!player.isFallFlying() || st.lastSpeedBps < p.preloadMinSpeed) {
                continue;
            }
            Vec3 v = player.getDeltaMovement();
            double h = Math.hypot(v.x, v.z);
            if (h < 0.05) {
                continue;
            }

            boolean freeze = p.hypersonicFreeze && st.lastSpeedBps >= p.hypersonicSpeed;
            if (freeze) {
                continue; // no NEW chunks unless preloaded - ride the shadow corridor
            }

            double dx = v.x / h;
            double dz = v.z / h;
            int corridor = (int) Math.min(p.maxCorridorChunks,
                    Math.ceil(st.lastSpeedBps * p.preloadAheadSeconds / 16.0));
            int half = p.corridorHalfWidthChunks;
            int budget = p.preloadTicketsPerTick;

            // Perpendicular unit vector for corridor width.
            double nx = -dz;
            double nz = dx;

            List<int[]> candidates = new ArrayList<>(); // {step, wOffsetAbs, wSigned}
            for (int step = 0; step <= corridor; step++) {
                for (int w = 0; w <= half; w++) {
                    candidates.add(new int[] {step, w, w});
                    if (w > 0) {
                        candidates.add(new int[] {step, w, -w});
                    }
                }
            }
            candidates.sort((a, b) -> {
                if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
                return Integer.compare(a[1], b[1]);
            });

            pp.worldKey = level.dimension();
            for (int[] cand : candidates) {
                if (budget <= 0 || pp.ticketed.size() >= p.maxTicketedChunksPerPlayer) {
                    break;
                }
                double cx = player.getX() + dx * cand[0] * 16.0 + nx * cand[2] * 16.0;
                double cz = player.getZ() + dz * cand[0] * 16.0 + nz * cand[2] * 16.0;
                long packed = ChunkPos.containing(BlockPos.containing(cx, 0, cz)).pack();
                if (pp.ticketed.containsKey(packed)) {
                    continue;
                }
                pp.ticketed.put(packed, now);
                level.getChunkSource().addTicketWithRadius(JETSTREAM_CORRIDOR,
                        ChunkPos.unpack(packed), 0);
                budget--;
            }
        }
    }

    /** Removes tickets older than the expiry; returns true if the player has nothing left. */
    private static boolean expireStale(ServerLevel level, PerPlayer pp, long now, JetStreamsConfig.Physics p) {
        if (pp.ticketed.isEmpty()) {
            return true;
        }
        boolean onlyMine = pp.worldKey == null || pp.worldKey == level.dimension();
        if (!onlyMine) {
            return false; // tickets live in another dimension's storage; its own tick loop won't purge them,
                          // but the disconnect handler or the other dimension's loop will.
        }
        Iterator<Map.Entry<Long, Long>> it = pp.ticketed.entrySet().iterator();
        boolean any = false;
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (now - e.getValue() > p.preloadExpiryTicks) {
                level.getChunkSource().removeTicketWithRadius(JETSTREAM_CORRIDOR, ChunkPos.unpack(e.getKey()), 0);
                it.remove();
            } else {
                any = true;
            }
        }
        return !any;
    }

    private static void releaseAll(ServerLevel level, PerPlayer pp) {
        for (Long packed : pp.ticketed.keySet()) {
            level.getChunkSource().removeTicketWithRadius(JETSTREAM_CORRIDOR, ChunkPos.unpack(packed), 0);
        }
        pp.ticketed.clear();
    }
}
