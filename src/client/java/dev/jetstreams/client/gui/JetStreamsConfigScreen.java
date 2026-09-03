package dev.jetstreams.client.gui;

import dev.jetstreams.client.TintPalette;
import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.registry.JetFlowOption;
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
 * ModMenu config screen - <b>visuals only</b> (v1.2.0). Every control here is client-local
 * and editable by anyone; physics settings live exclusively in the config file
 * (operator territory, applied via {@code /jetstreams reload}).
 */
public class JetStreamsConfigScreen extends Screen {
    private final Screen parent;
    private String status = "";

    public JetStreamsConfigScreen(Screen parent) {
        super(Component.literal("Elytra Jet Streams"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int colW = Math.min(220, (w - 36) / 2);
        int x0 = 10;
        int x1 = w - 10 - colW;

        this.addRenderableOnly(new LabelWidget(0, 12, w, 14, this.title));

        var c = ConfigManager.get().client;
        this.addRenderableWidget(toggle(x0, 56, colW, "Directional particles",
                () -> c.directionParticles, v -> c.directionParticles = v));
        this.addRenderableWidget(toggle(x0, 80, colW, "Stream tints",
                () -> c.streamTints, v -> c.streamTints = v));
        this.addRenderableWidget(toggle(x0, 104, colW, "Headwind edge tint",
                () -> c.headwindIndicator, v -> c.headwindIndicator = v));
        this.addRenderableWidget(toggle(x0, 128, colW, "Sky darkening",
                () -> c.skyTint, v -> c.skyTint = v));
        this.addRenderableWidget(toggle(x0, 152, colW, "Cirrus cloud bands",
                () -> c.cirrusBands, v -> c.cirrusBands = v));

        this.addRenderableWidget(slider(x1, 56, colW, "Particle density", 0.0, 6.0, 0.1,
                () -> c.particleDensity, v -> c.particleDensity = v, JetStreamsConfigScreen::fmt1));
        this.addRenderableWidget(slider(x1, 80, colW, "Tint strength", 0.0, 2.0, 0.05,
                () -> c.tintStrength, v -> c.tintStrength = v, JetStreamsConfigScreen::fmt2));
        this.addRenderableWidget(slider(x1, 104, colW, "Sky tint strength", 0.0, 2.0, 0.05,
                () -> c.skyTintStrength, v -> c.skyTintStrength = v, JetStreamsConfigScreen::fmt2));

        // Colour editors: one button per tint slot, opening the HSV colour wheel.
        int cy = Math.min(200, h - 80);
        int bw = Math.max(64, (w - 20 - 4 * 4) / 5);
        this.addRenderableOnly(new LabelWidget(0, cy - 16, w, 12,
                Component.literal("Tint colours - click to open the colour wheel")));
        this.addRenderableWidget(colorButton(10, cy, bw, "N", () -> c.tintNorth, v -> c.tintNorth = v));
        this.addRenderableWidget(colorButton(10 + (bw + 4), cy, bw, "S", () -> c.tintSouth, v -> c.tintSouth = v));
        this.addRenderableWidget(colorButton(10 + (bw + 4) * 2, cy, bw, "E", () -> c.tintEast, v -> c.tintEast = v));
        this.addRenderableWidget(colorButton(10 + (bw + 4) * 3, cy, bw, "W", () -> c.tintWest, v -> c.tintWest = v));
        this.addRenderableWidget(colorButton(10 + (bw + 4) * 4, cy, bw, "Neutral", () -> c.tintNeutral, v -> c.tintNeutral = v));

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(w - 70, h - 28, 60, 20).build());
        this.addRenderableOnly(new LabelWidget(0, h - 24, w, 12,
                Component.literal(this.status.isEmpty()
                        ? "§7Visual settings apply instantly and stay on this client."
                        : this.status)));
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

    /** Opens the HSV colour wheel editor for one tint slot (live preview, cancel restores). */
    private Button colorButton(int x, int y, int w, String label,
                               Supplier<String> get, Consumer<String> set) {
        String hex = shortHex(get.get());
        return Button.builder(Component.literal(label + " " + hex), b ->
                this.minecraft.setScreen(new ColorWheelScreen(this, label,
                        TintPalette.parse(get.get()),
                        rgb -> set.accept(String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF)))
        )).bounds(x, y, w, 20).build();
    }

    private static String shortHex(String hex) {
        return String.format(Locale.ROOT, "#%06X", TintPalette.parse(hex) & 0xFFFFFF);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    @Override
    public void onClose() {
        ConfigManager.save(); // persists client visuals
        this.minecraft.setScreen(this.parent);
    }
}
