package dev.jetstreams.client.mixinhooks;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.field.WindField;
import dev.jetstreams.physics.PhysicsResolver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * Exponential sky darkening with altitude, sharing the rate-constant family of the speed
 * equations: {@code 1 - exp(-k * (y - activation))} reaching ~88% darkening at y=8000.
 * Applied to the sky color and (via the fog mixin) the horizon fog so the two stay glued.
 */
public final class SkyTint {
    /** Deep night-navy target color. */
    public static final int DEEP_NAVY = 0xFF0A1A33;

    private SkyTint() {}

    public static void init() {}

    public static int apply(ClientLevel level, int skyColor, Vec3 cameraPos) {
        JetStreamsConfig.Client c = ConfigManager.get().client;
        if (!c.skyTint || skyColor == 0) {
            return skyColor;
        }
        JetStreamsConfig.Physics p = PhysicsResolver.activeFor(level);
        if (!WindField.enabledFor(level, p)) {
            return skyColor;
        }
        double darken = WindField.skyDarkening(p, cameraPos.y) * Math.min(1.0, c.skyTintStrength);
        if (darken <= 0.0) {
            return skyColor;
        }
        return ARGB.srgbLerp((float) Math.min(1.0, darken), skyColor, DEEP_NAVY);
    }
}
