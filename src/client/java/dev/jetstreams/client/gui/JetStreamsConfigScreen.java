package dev.jetstreams.client.gui;

import com.google.gson.Gson;
import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.config.JetStreamsConfig;
import dev.jetstreams.network.UpdatePhysicsPayload;
import dev.jetstreams.physics.RemotePhysics;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Config screen (opened via ModMenu). Visual settings are client-local and editable by
 * everyone; physics settings are staged locally and applied through the server, which
 * validates operator permission before saving, so the k constant and friends can only be
 * changed by operators.
 */
public class JetStreamsConfigScreen extends Screen {
    private static final int[] PALETTE = {
            0x7FB4FF, 0xFFB454, 0x59E0A0, 0xC77DFF, 0x8C99A8, 0xFF6B6B,
            0x6BD6FF, 0xFFE066, 0x9FE870, 0xFF9FE0, 0xFFFFFF, 0x2E3338
    };

    private static final int PAGE_VISUALS = 0;
    private static final int PAGE_FLIGHT = 1;
    private static final int PAGE_WORLD = 2;

    private static final Gson GSON = new Gson();

    private final Screen parent;
    private final JetStreamsConfig.Physics staged;
    private int page = PAGE_VISUALS;
    private String status = "";

    public JetStreamsConfigScreen(Screen parent) {
        super(Component.literal("Elytra Jet Streams"));
        this.parent = parent;
        // Stage physics from the authoritative copy: local in singleplayer, the server
        // sync on remote servers - so widgets start showing the values actually in effect.
        JetStreamsConfig.Physics base = Minecraft.getInstance().hasSingleplayerServer()
                || RemotePhysics.overrideOrNull() == null
                ? ConfigManager.get().physics
                : RemotePhysics.overrideOrNull();
        JetStreamsConfig.Physics clone = GSON.fromJson(GSON.toJson(base), JetStreamsConfig.Physics.class);
        this.staged = clone != null ? clone : new JetStreamsConfig.Physics();
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int colW = Math.min(200, (w - 36) / 2);
        int x0 = 10;
        int x1 = w - 10 - colW;
        int y0 = 56; // first widget row

        // Title + page tabs.
        this.addRenderableOnly(new LabelWidget(0, 12, w, 14, this.title));
        int tabW = Math.min(150, (w - 40) / 3);
        int tabsX = (w - tabW * 3 - 8) / 2;
        this.addRenderableWidget(tab(tabsX, 30, tabW, "Visuals", PAGE_VISUALS));
        this.addRenderableWidget(tab(tabsX + tabW + 4, 30, tabW, "Flight speed", PAGE_FLIGHT));
        this.addRenderableWidget(tab(tabsX + (tabW + 4) * 2, 30, tabW, "World & perf", PAGE_WORLD));

        if (this.page == PAGE_VISUALS) {
            var c = ConfigManager.get().client;
            this.addRenderableWidget(toggle(x0, y(0), colW, "Directional particles",
                    () -> c.directionParticles, v -> c.directionParticles = v));
            this.addRenderableWidget(toggle(x0, y(1), colW, "Stream tints",
                    () -> c.streamTints, v -> c.streamTints = v));
            this.addRenderableWidget(toggle(x0, y(2), colW, "Headwind edge tint",
                    () -> c.headwindIndicator, v -> c.headwindIndicator = v));
            this.addRenderableWidget(toggle(x0, y(3), colW, "Sky darkening",
                    () -> c.skyTint, v -> c.skyTint = v));
            this.addRenderableWidget(toggle(x0, y(4), colW, "Cirrus cloud bands",
                    () -> c.cirrusBands, v -> c.cirrusBands = v));
            this.addRenderableWidget(slider(x1, y(0), colW, "Particle density", 0.0, 4.0, 0.1,
                    () -> c.particleDensity, v -> c.particleDensity = v, JetStreamsConfigScreen::fmt2));
            this.addRenderableWidget(slider(x1, y(1), colW, "Tint strength", 0.0, 2.0, 0.05,
                    () -> c.tintStrength, v -> c.tintStrength = v, JetStreamsConfigScreen::fmt2));
            this.addRenderableWidget(slider(x1, y(2), colW, "Sky tint strength", 0.0, 2.0, 0.05,
                    () -> c.skyTintStrength, v -> c.skyTintStrength = v, JetStreamsConfigScreen::fmt2));
            // Color pickers: one row of cycle buttons near the bottom of the column area.
            int cy = y(6);
            int bw = Math.max(56, (w - 20 - 4 * 4) / 5);
            this.addRenderableWidget(colorButton(10, cy, bw, "N", () -> c.tintNorth, v -> c.tintNorth = v));
            this.addRenderableWidget(colorButton(10 + (bw + 4), cy, bw, "S", () -> c.tintSouth, v -> c.tintSouth = v));
            this.addRenderableWidget(colorButton(10 + (bw + 4) * 2, cy, bw, "E", () -> c.tintEast, v -> c.tintEast = v));
            this.addRenderableWidget(colorButton(10 + (bw + 4) * 3, cy, bw, "W", () -> c.tintWest, v -> c.tintWest = v));
            this.addRenderableWidget(colorButton(10 + (bw + 4) * 4, cy, bw, "Neutral", () -> c.tintNeutral, v -> c.tintNeutral = v));
        }

        if (this.page == PAGE_FLIGHT) {
            this.addRenderableWidget(toggle(x0, y(0), colW, "Jet streams enabled",
                    this::getEnabled, this::setEnabled));
            this.addRenderableWidget(slider(x1, y(0), colW, "Activation altitude k", -64.0, 4000.0, 10.0,
                    () -> staged.activationAltitude, v -> staged.activationAltitude = v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(1), colW, "Multiplier rate", 0.0, 0.002, 0.00001,
                    () -> staged.multiplierRate, v -> staged.multiplierRate = v, JetStreamsConfigScreen::fmt5));
            this.addRenderableWidget(slider(x1, y(1), colW, "Speed cap altitude", 1000.0, 20000.0, 100.0,
                    () -> staged.speedCapAltitude, v -> staged.speedCapAltitude = v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(2), colW, "Firework kick/tick", 0.1, 20.0, 0.1,
                    () -> staged.fireworkKickPerTick, v -> staged.fireworkKickPerTick = v, JetStreamsConfigScreen::fmt1));
            this.addRenderableWidget(slider(x1, y(2), colW, "Cruise start altitude", 300.0, 12000.0, 50.0,
                    () -> staged.cruiseStartAltitude, v -> staged.cruiseStartAltitude = v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(3), colW, "Cruise speed base", 1.0, 100.0, 1.0,
                    () -> staged.cruiseSpeedBase, v -> staged.cruiseSpeedBase = v, JetStreamsConfigScreen::fmt1));
            this.addRenderableWidget(slider(x1, y(3), colW, "Cruise speed peak", 20.0, 400.0, 5.0,
                    () -> staged.cruiseSpeedPeak, v -> staged.cruiseSpeedPeak = v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(4), colW, "Tailwind speed", 0.0, 200.0, 1.0,
                    () -> staged.windBoostSpeed, v -> staged.windBoostSpeed = v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x1, y(4), colW, "Wind acceleration", 1.0, 100.0, 1.0,
                    () -> staged.windAccelPerSecondSq, v -> staged.windAccelPerSecondSq = v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(5), colW, "Headwind drag", 0.0, 0.1, 0.001,
                    () -> staged.headwindDragPerTick, v -> staged.headwindDragPerTick = v, JetStreamsConfigScreen::fmt3));
            this.addRenderableWidget(slider(x1, y(5), colW, "Boost min alignment", 0.0, 0.95, 0.05,
                    () -> staged.boostMinAlignment, v -> staged.boostMinAlignment = v, JetStreamsConfigScreen::fmt2));
            this.addRenderableWidget(slider(x0, y(6), colW, "Boost full alignment", 0.05, 1.0, 0.05,
                    () -> staged.boostFullAlignment, v -> staged.boostFullAlignment = v, JetStreamsConfigScreen::fmt2));
            this.addRenderableWidget(slider(x1, y(6), colW, "Cruise core threshold", 0.0, 1.0, 0.05,
                    () -> staged.coreProfileForCruise, v -> staged.coreProfileForCruise = v, JetStreamsConfigScreen::fmt2));
        }

        if (this.page == PAGE_WORLD) {
            this.addRenderableWidget(slider(x0, y(0), colW, "Stream width min", 64.0, 4096.0, 8.0,
                    () -> staged.streamWidthMin, v -> staged.streamWidthMin = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x1, y(0), colW, "Stream width max", 64.0, 8192.0, 8.0,
                    () -> staged.streamWidthMax, v -> staged.streamWidthMax = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(1), colW, "Dead zone min", 0.0, 4096.0, 8.0,
                    () -> staged.deadZoneMin, v -> staged.deadZoneMin = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x1, y(1), colW, "Dead zone max", 0.0, 8192.0, 8.0,
                    () -> staged.deadZoneMax, v -> staged.deadZoneMax = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(CycleButton.<String>builder(
                            s -> Component.literal("Direction mode: " + s), () -> staged.directionMode)
                    .withValues("ALTERNATING", "RANDOM")
                    .create(x0, y(2), colW, 20, Component.literal("Direction mode"),
                            (btn, val) -> staged.directionMode = val));
            this.addRenderableWidget(slider(x1, y(2), colW, "Meander amplitude", 0.0, 600.0, 10.0,
                    () -> staged.meanderAmplitude, v -> staged.meanderAmplitude = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(3), colW, "Meander wavelength", 500.0, 10000.0, 100.0,
                    () -> staged.meanderWavelength, v -> staged.meanderWavelength = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(toggle(x1, y(3), colW, "Chunk corridor preloading",
                    () -> staged.chunkPreloadEnabled, v -> staged.chunkPreloadEnabled = v));
            this.addRenderableWidget(slider(x0, y(4), colW, "Preload ahead (s)", 0.5, 10.0, 0.5,
                    () -> staged.preloadAheadSeconds, v -> staged.preloadAheadSeconds = v, JetStreamsConfigScreen::fmt1));
            this.addRenderableWidget(slider(x1, y(4), colW, "Preload tickets/tick", 1.0, 16.0, 1.0,
                    () -> staged.preloadTicketsPerTick, v -> staged.preloadTicketsPerTick = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x0, y(5), colW, "Ticketed chunks cap", 8.0, 1024.0, 8.0,
                    () -> staged.maxTicketedChunksPerPlayer, v -> staged.maxTicketedChunksPerPlayer = (int) v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(slider(x1, y(5), colW, "Hypersonic freeze speed", 50.0, 1000.0, 10.0,
                    () -> staged.hypersonicSpeed, v -> staged.hypersonicSpeed = v, JetStreamsConfigScreen::fmt0));
            this.addRenderableWidget(toggle(x0, y(6), colW, "Hypersonic chunk freeze",
                    () -> staged.hypersonicFreeze, v -> staged.hypersonicFreeze = v));
        }

        // Bottom bar: apply (physics) + done + status line.
        boolean remoteServer = Minecraft.getInstance().getCurrentServer() != null;
        if (this.page != PAGE_VISUALS) {
            this.addRenderableWidget(Button.builder(Component.literal("Apply to server"), b -> applyPhysics())
                    .bounds(10, h - 28, 110, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(w - 70, h - 28, 60, 20).build());
        String note = remoteServer
                ? "§7Physics changes need §foperator§7 status on this server."
                : "§7You own this world - physics apply directly.";
        this.addRenderableOnly(new LabelWidget(0, h - 24, w, 12, Component.literal(note)));
    }

    private int y(int slot) {
        return 56 + slot * 24;
    }

    private Button tab(int x, int y, int w, String label, int target) {
        return Button.builder(Component.literal(label), b -> {
            this.page = target;
            this.rebuildWidgets();
        }).bounds(x, y, w, 18).build();
    }

    // ----------------------------------------------------------------- helpers

    private boolean getEnabled() {
        return staged.enabled;
    }

    private void setEnabled(boolean v) {
        staged.enabled = v;
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String label,
                                        Supplier<Boolean> get, Consumer<Boolean> set) {
        return CycleButton.onOffBuilder(get.get())
                .create(x, y, w, 20, Component.literal(label), (btn, val) -> set.accept(val));
    }

    private AbstractSliderButton slider(int x, int y, int w, String label,
                                        double min, double max, double step,
                                        DoubleSupplier get, java.util.function.DoubleConsumer set,
                                        DoubleFunction<String> fmt) {
        double initial = Mth.clamp((get.getAsDouble() - min) / (max - min), 0.0, 1.0);
        AbstractSliderButton slider = new AbstractSliderButton(x, y, w, 20, Component.empty(), initial) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal(label + ": " + fmt.apply(get.getAsDouble())));
            }

            @Override
            protected void applyValue() {
                double raw = min + this.value * (max - min);
                set.accept(step <= 0.0 ? raw : Math.round(raw / step) * step);
            }
        };
        slider.setMessage(Component.literal(label + ": " + fmt.apply(get.getAsDouble())));
        return slider;
    }

    private Button colorButton(int x, int y, int w, String label,
                               Supplier<String> get, Consumer<String> set) {
        String hex = get.get();
        return Button.builder(Component.literal(label + " " + hex.toLowerCase(Locale.ROOT)), b -> {
            int current = dev.jetstreams.client.TintPalette.parse(get.get());
            int idx = 0;
            for (int i = 0; i < PALETTE.length; i++) {
                if (PALETTE[i] == current) {
                    idx = i;
                    break;
                }
            }
            int next = PALETTE[(idx + 1) % PALETTE.length];
            String hexNext = String.format(Locale.ROOT, "#%06X", next);
            set.accept(hexNext);
            b.setMessage(Component.literal(label + " " + hexNext.toLowerCase(Locale.ROOT)));
        }).bounds(x, y, w, 20).build();
    }

    private void applyPhysics() {
        String json = GSON.toJson(staged);
        var integrated = Minecraft.getInstance().getSingleplayerServer();
        if (integrated != null) {
            integrated.execute(() -> {
                if (ConfigManager.applyPhysicsJson(json)) {
                    dev.jetstreams.field.WindField.invalidate();
                    dev.jetstreams.network.Networking.broadcastAll(integrated);
                }
            });
            this.status = "§aApplied locally.";
        } else {
            ClientPlayNetworking.send(new UpdatePhysicsPayload(json));
            this.status = "§aSent - the server decides (operator required).";
        }
    }

    private static String fmt0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String fmt3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String fmt5(double v) {
        return String.format(Locale.ROOT, "%.5f", v);
    }

    // ------------------------------------------------------------------ chrome

    @Override
    public void onClose() {
        ConfigManager.save(); // persists client visuals + staged physics file copy
        this.minecraft.setScreen(this.parent);
    }
}
