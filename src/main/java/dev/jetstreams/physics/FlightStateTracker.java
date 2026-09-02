package dev.jetstreams.physics;

import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-side storage of {@link FlightState} keyed by player UUID. */
public final class FlightStateTracker {
    private static final Map<UUID, FlightState> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, FlightState> CLIENT = new ConcurrentHashMap<>();

    private FlightStateTracker() {}

    public static FlightState get(Level level, UUID playerId) {
        Map<UUID, FlightState> map = level.isClientSide() ? CLIENT : SERVER;
        return map.computeIfAbsent(playerId, id -> new FlightState());
    }

    public static FlightState getClient(UUID playerId) {
        return CLIENT.computeIfAbsent(playerId, id -> new FlightState());
    }

    public static void clear(boolean clientSide) {
        (clientSide ? CLIENT : SERVER).clear();
    }
}
