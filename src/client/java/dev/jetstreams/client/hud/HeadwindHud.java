package dev.jetstreams.client.hud;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.physics.FlightState;
import dev.jetstreams.physics.FlightStateTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Subtle current indicator:
 * <ul>
 *   <li>a faint warm gradient at the screen edges while fighting the current, and</li>
 *   <li>a small chevron under the crosshair rotating with the stream's flow direction
 *       relative to the view - it points the way the current carries you.</li>
 * </ul>
 */
public final class HeadwindHud {
    private HeadwindHud() {}

    public static void register() {
        HudElementRegistry.addLast(dev.jetstreams.JetStreams.id("headwind"), HeadwindHud::extract);
    }

    private static void extract(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        if (!ConfigManager.get().client.headwindIndicator) {
            return;
        }
        FlightState st = FlightStateTracker.get(player.level(), player.getUUID());
        int w = gui.guiWidth();
        int h = gui.guiHeight();

        float factor = (float) st.headwindFactor;
        if (factor > 0.04F) {
            // Warm ember tint, capped at ~15% alpha.
            int alpha = (int) (factor * 38.0F);
            int col = (alpha << 24) | 0xFF3A18;
            gui.fillGradient(0, 0, w, 22, col, 0);
            gui.fillGradient(0, h - 22, w, h, 0, col);
        }

        if (st.inStream && st.windProfile > 0.05) {
            double wx = st.windX;
            double wz = st.windZ;
            if (wx * wx + wz * wz > 1.0e-6) {
                float yaw = player.getYRot();
                double windYawDeg = Math.toDegrees(Math.atan2(-wx, wz));
                float rel = Mth.wrapDegrees((float) (windYawDeg - yaw));
                int alpha = (int) (70.0 + 100.0 * st.headwindFactor + 40.0 * st.windProfile);
                int col = ((Math.min(alpha, 200)) << 24)
                        | (st.headwind ? 0xFF6040 : 0xBFD8FF);
                Matrix3x2fStack pose = gui.pose();
                pose.pushMatrix();
                pose.translate(w / 2.0F, h / 2.0F + 20.0F);
                pose.rotate((float) Math.toRadians(rel));
                // Chevron pointing "up" = flow direction straight ahead.
                gui.fill(-1, -15, 1, -5, col);
                gui.fill(-4, -8, 4, -5, col);
                pose.popMatrix();
            }
        }
    }
}
