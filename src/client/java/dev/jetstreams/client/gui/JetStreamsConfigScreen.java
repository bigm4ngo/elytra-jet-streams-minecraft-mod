package dev.jetstreams.client.gui;

import dev.jetstreams.client.TintPalette;
import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.config.JetStreamsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * ModMenu config screen - <b>visuals only</b>. Every control here is client-local and
 * editable by anyone; physics settings live exclusively in the config file (operator
 * territory, applied via {@code /jetstreams reload}).
 *
 * <p>Layout is deliberately non-overlapping: fixed rows for toggles/sliders up top, and a
 * bottom-anchored colour block (colour buttons + per-direction tint switches) above the
 * Done button, so it never covers another setting at any GUI scale.
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
        // Left column: behaviour toggles. Right column: strength sliders.
        this.addRenderableWidget(toggle(x0, 36, colW, "Directional particles",
                () -> c.directionParticles, v -> c.directionParticles = v));
        this.addRenderableWidget(toggle(x0, 58, colW, "Stream tints",
                () -> c.streamTints, v -> c.streamTints = v));
        this.addRenderableWidget(toggle(x0, 80, colW, "Headwind edge tint",
                () -> c.headwindIndicator, v -> c.headwindIndicator = v));
        this.addRenderableWidget(toggle(x0, 102, colW, "Sky darkening",
                () -> c.skyTint, v -> c.skyTint = v));
        this.addRenderableWidget(toggle(x0, 124, colW, "Cirrus cloud bands",
                () -> c.cirrusBands, v -> c.cirrusBands = v));

        this.addRenderableWidget(slider(x1, 36, colW, "Particle density", 0.0, 6.0, 0.1,
                () -> c.particleDensity, v -> c.particleDensity = v, JetStreamsConfigScreen::fmt1));
        this.addRenderableWidget(slider(x1, 58, colW, "Tint strength", 0.0, 2.0, 0.05,
                () -> c.tintStrength, v -> c.tintStrength = v, JetStreamsConfigScreen::fmt2));
        this.addRenderableWidget(slider(x1, 80, colW, "Sky tint strength", 0.0, 2.0, 0.05,
                () -> c.skyTintStrength, v -> c.skyTintStrength = v, JetStreamsConfigScreen::fmt2));

        // Bottom-anchored colour block: never overlaps the rows above or the Done button.
        int bw = Math.max(56, (w - 20 - 4 * 4) / 5);
        int colourToggleY = Math.min(186, h - 42);
        int colourBtnY = colourToggleY - 20;
        int colourLabelY = colourBtnY - 15;

        this.addRenderableOnly(new LabelWidget(0, colourLabelY, w, 12,
                Component.literal("Tint colours - click a button to edit (colour wheel + hex code)")));
        this.addRenderableWidget(colorButton(10, colourBtnY, bw, "N",
                () -> c.tintNorth, v -> c.tintNorth = v, () -> c.tintNorthEnabled));
        this.addRenderableWidget(colorButton(10 + (bw + 4), colourBtnY, bw, "S",
                () -> c.tintSouth, v -> c.tintSouth = v, () -> c.tintSouthEnabled));
        this.addRenderableWidget(colorButton(10 + (bw + 4) * 2, colourBtnY, bw, "E",
                () -> c.tintEast, v -> c.tintEast = v, () -> c.tintEastEnabled));
        this.addRenderableWidget(colorButton(10 + (bw + 4) * 3, colourBtnY, bw, "W",
                () -> c.tintWest, v -> c.tintWest = v, () -> c.tintWestEnabled));
        this.addRenderableWidget(colorButton(10 + (bw + 4) * 4, colourBtnY, bw, "Neutral",
                () -> c.tintNeutral, v -> c.tintNeutral = v, () -> c.tintNeutralEnabled));

        // Per-direction tint switches (screen tint for each flow direction + neutral).
        this.addRenderableWidget(tintSwitch(10, colourToggleY, bw, "N",
                () -> c.tintNorthEnabled, v -> c.tintNorthEnabled = v));
        this.addRenderableWidget(tintSwitch(10 + (bw + 4), colourToggleY, bw, "S",
                () -> c.tintSouthEnabled, v -> c.tintSouthEnabled = v));
        this.addRenderableWidget(tintSwitch(10 + (bw + 4) * 2, colourToggleY, bw, "E",
                () -> c.tintEastEnabled, v -> c.tintEastEnabled = v));
        this.addRenderableWidget(tintSwitch(10 + (bw + 4) * 3, colourToggleY, bw, "W",
                () -> c.tintWestEnabled, v -> c.tintWestEnabled = v));
        this.addRenderableWidget(tintSwitch(10 + (bw + 4) * 4, colourToggleY, bw, "Neutral",
                () -> c.tintNeutralEnabled, v -> c.tintNeutralEnabled = v));

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(w - 70, h - 26, 60, 20).build());
        this.addRenderableOnly(new LabelWidget(0, colourToggleY + 20, w, 12,
                Component.literal(this.status.isEmpty()
                        ? "§7Switches under the colours enable/disable each direction's tint."
                        : this.status)));
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String label,
                                        Supplier<Boolean> get, Consumer<Boolean> set) {
        return CycleButton.onOffBuilder(get.get())
                .create(x, y, w, 20, Component.literal(label), (btn, val) -> set.accept(val));
    }

    private AbstractWidget tintSwitch(int x, int y, int w, String label,
                                      Supplier<Boolean> get, Consumer<Boolean> set) {
        Button button = Button.builder(Component.empty(), b -> {
            set.accept(!get.get());
            b.setMessage(switchText(label, get.get()));
        }).bounds(x, y, w, 16).build();
        button.setMessage(switchText(label, get.get()));
        return button;
    }

    private static Component switchText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "§aon" : "§7off"));
    }

    /** Opens the colour editor (wheel + hex) for one tint slot; live preview, cancel restores. */
    private AbstractWidget colorButton(int x, int y, int w, String label,
                                       Supplier<String> get, Consumer<String> set,
                                       Supplier<Boolean> enabledGet) {
        String hex = String.format(Locale.ROOT, "#%06X", TintPalette.parse(get.get()) & 0xFFFFFF);
        Button button = Button.builder(Component.literal(label + " " + hex),
                b -> this.minecraft.setScreen(new ColorWheelScreen(this, label,
                        TintPalette.parse(get.get()),
                        rgb -> set.accept(String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF))))
        ).bounds(x, y, w, 18).build();
        this.addRenderableOnly(swatch(get, enabledGet, x + w - 15, y + 3));
        return button;
    }

    /** Render-only colour chip that sits on top of a colour button (never blocks clicks). */
    private AbstractWidget swatch(Supplier<String> get, Supplier<Boolean> enabledGet, int x, int y) {
        return new AbstractWidget(x, y, 12, 12, Component.empty()) {
            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                    int mouseX, int mouseY, float a) {
                int rgb = TintPalette.parse(get.get());
                int alpha = enabledGet.get() ? 0xFF000000 : 0x66000000;
                graphics.fill(getX(), getY(), getX() + width, getY() + height, alpha | rgb);
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput o) {
                // decorative chip on top of a narrated button
            }
        };
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
