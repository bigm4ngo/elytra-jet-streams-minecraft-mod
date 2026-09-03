package dev.jetstreams.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Full-screen HSV colour editor for one tint slot: wheel (hue + saturation), brightness
 * slider, live preview swatch and hex readout. Changes apply live to the target config
 * field; Cancel restores the colour the editor was opened with.
 */
public class ColorWheelScreen extends Screen {
    private static final int WHEEL = 120;

    private final Screen parent;
    private final String title;
    private final IntConsumer target;
    private final int originalRgb;

    private ColorWheelWidget wheel;
    private float value = 1.0F;

    public ColorWheelScreen(Screen parent, String title, int rgb, IntConsumer target) {
        super(Component.literal("Edit colour - " + title));
        this.parent = parent;
        this.title = title;
        this.target = target;
        this.originalRgb = rgb;
        float[] hsv = rgbToHsv(rgb);
        this.value = hsv[2];
    }

    /** Returns {hue, saturation, value} in 0..1. */
    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0.0F;
        if (d > 0.0F) {
            if (max == r) {
                h = ((g - b) / d) % 6.0F;
            } else if (max == g) {
                h = (b - r) / d + 2.0F;
            } else {
                h = (r - g) / d + 4.0F;
            }
            h /= 6.0F;
            if (h < 0.0F) {
                h += 1.0F;
            }
        }
        float s = max <= 0.0F ? 0.0F : d / max;
        return new float[]{h, s, max};
    }

    @Override
    protected void init() {
        int w = this.width;
        int cx = w / 2 - WHEEL / 2;
        float[] hsv = rgbToHsv(currentColor());
        this.wheel = new ColorWheelWidget(cx, 44, WHEEL, hsv[0], hsv[1]);
        this.addRenderableWidget(this.wheel);

        this.addRenderableWidget(new AbstractSliderButton(
                cx, 44 + WHEEL + 12, WHEEL, 18,
                Component.literal(brightnessLabel()), value) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal(brightnessLabel()));
            }

            @Override
            protected void applyValue() {
                value = (float) this.value;
                apply();
            }
        });
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(w / 2 - 100, this.height - 30, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            this.target.accept(originalRgb);
            this.minecraft.setScreen(parent);
        }).bounds(w / 2 + 2, this.height - 30, 98, 20).build());

        this.addRenderableOnly(new LabelWidget(0, 12, w, 14, Component.literal(this.title)));
        this.addRenderableOnly(new LabelWidget(0, 44 + WHEEL + 34, w, 12,
                Component.literal("Click or drag inside the wheel to pick hue + saturation")));
    }

    private String brightnessLabel() {
        return String.format(Locale.ROOT, "Brightness: %d%%", (int) (value * 100));
    }

    private int currentColor() {
        return wheel != null ? wheel.currentRgb(value) : originalRgb;
    }

    private void apply() {
        this.target.accept(currentColor());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int rgb = currentColor();
        int size = 34;
        int x = this.width / 2 - size / 2;
        int y = 44 + WHEEL + 52;
        graphics.fill(x, y, x + size, y + size, 0xFF000000 | rgb);
        graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFFFFFFFF);
        graphics.text(this.font, String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF),
                x + size + 8, y + size / 2 - 4, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
