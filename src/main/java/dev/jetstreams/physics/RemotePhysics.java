package dev.jetstreams.physics;

import com.google.gson.Gson;
import dev.jetstreams.config.JetStreamsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side holder for the physics config + field seed synced from the server.
 * Lives in common code so {@link WindField} can read it without touching client classes.
 */
public final class RemotePhysics {
    private static final Gson GSON = new Gson();

    private static volatile @Nullable JetStreamsConfig.Physics override;
    private static volatile boolean hasFieldSeed;
    private static volatile long fieldSeed;

    private RemotePhysics() {}

    /** Applies a synced payload. Called on the client only. */
    public static void accept(String json, long seed) {
        try {
            JetStreamsConfig.Physics parsed = GSON.fromJson(json, JetStreamsConfig.Physics.class);
            if (parsed != null) {
                override = parsed;
            }
        } catch (Exception ignored) {
            // Malformed payload - keep the previous config.
        }
        fieldSeed = seed;
        hasFieldSeed = true;
    }

    public static @Nullable JetStreamsConfig.Physics overrideOrNull() {
        return override;
    }

    public static boolean hasFieldSeed() {
        return hasFieldSeed;
    }

    public static long fieldSeed() {
        return fieldSeed;
    }

    /** Clears state on client disconnect so the next session starts fresh. */
    public static void clear() {
        override = null;
        hasFieldSeed = false;
        fieldSeed = 0L;
    }
}
