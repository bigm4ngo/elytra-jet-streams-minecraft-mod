package dev.jetstreams.physics;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.config.JetStreamsConfig;
import net.minecraft.world.level.Level;

/**
 * Picks the physics configuration that simulation should use on this side.
 *
 * <p>The server is authoritative and uses its own config file. Clients use the config the
 * server synced to them ({@link RemotePhysics}) so flight prediction matches server physics
 * exactly; before the sync arrives they fall back to their local file, which is always
 * correct in singleplayer.
 */
public final class PhysicsResolver {
    private PhysicsResolver() {}

    public static JetStreamsConfig.Physics activeFor(Level level) {
        if (level.isClientSide()) {
            JetStreamsConfig.Physics override = RemotePhysics.overrideOrNull();
            if (override != null) {
                return override;
            }
        }
        return ConfigManager.get().physics;
    }
}
