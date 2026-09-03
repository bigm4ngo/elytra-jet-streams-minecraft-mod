package dev.jetstreams.client.hud;

import dev.jetstreams.client.TintPalette;
import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.physics.FlightState;
import dev.jetstreams.physics.FlightStateTracker;
import dev.jetstreams.physics.PhysicsResolver;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Subtle current indicators:
 * <ul>
 *   <li>a full-screen tint colored by the dominant current's cardinal direction
 *       (configurable per direction, plus a neutral tone for dead zones), and</li>
 *   <li>a faint warm gradient at the screen edges while fighting the current.</li>
 * </ul>
 * Both are toggleable in the config screen; the old crosshair chevron was removed.
 */
public final class StreamOverlayHud {
    private StreamOverlayHud() {}

    public static void register() {
        HudElementRegistry.addLast(dev.jetstreams.JetStreams.id("stream_overlay"), StreamOverlayHud::extract);
    }

    private static void extract(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !player.isFallFlying()) {
            return;
        }
        var c = ConfigManager.get().client;
        FlightState st = FlightStateTracker.get(player.level(), player.getUUID());
        if (!c.streamTints && !c.headwindIndicator) {
            return;
        }
        // Tints belong to the stream layer: nothing shows below the activation altitude.
        if (player.getY() < PhysicsResolver.activeFor(player.level()).activationAltitude) {
            return;
        }
        int w = gui.guiWidth();
        int h = gui.guiHeight();

        // ---------------------------------------------- directional / neutral tint
        if (c.streamTints) {
            double strength = Math.max(0.0, Math.min(2.0, c.tintStrength));
            if (st.inStream && st.windProfile > 0.05 && st.flowDir >= 0) {
                int rgb = TintPalette.forDirection(st.flowDir);
                int alpha = (int) Math.min(90.0, 62.0 * strength * Math.min(1.0, st.windProfile));
                if (alpha > 0) {
                    gui.fill(0, 0, w, h, (alpha << 24) | rgb);
                }
            } else if (!st.inStream) {
                int rgb = TintPalette.neutral();
                int alpha = (int) Math.min(40.0, 24.0 * strength);
                if (alpha > 0) {
                    gui.fill(0, 0, w, h, (alpha << 24) | rgb);
                }
            }
        }

        // ------------------------------------------------------- headwind edge tint
        if (c.headwindIndicator) {
            float factor = (float) st.headwindFactor;
            if (factor > 0.04F) {
                // Warm ember tint, capped at ~15% alpha.
                int alpha = (int) (factor * 38.0F);
                int col = (alpha << 24) | 0xFF3A18;
                gui.fillGradient(0, 0, w, 22, col, 0);
                gui.fillGradient(0, h - 22, w, h, 0, col);
            }
        }
    }
}
