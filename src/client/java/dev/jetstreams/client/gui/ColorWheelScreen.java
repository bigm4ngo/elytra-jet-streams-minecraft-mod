package dev.jetstreams.client.gui;

import dev.jetstreams.client.TintPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Full-screen HSV colour editor for one tint slot with <b>both</b> input methods: the
 * colour wheel (hue + saturation by click/drag) and a hex text field (#RRGGBB). A
 * brightness slider and a live preview swatch complete the editor. Changes apply live to
 * the target config field; Cancel restores the colour the editor was opened with.
 */
public class ColorWheelScreen extends Screen {
    private static final int WHEEL = 96; // matches the generated wheel texture 1:1

    private final Screen parent;
    private final String title;
    private final IntConsumer target;
    private final int originalRgb;

    private ColorWheelWidget wheel;
    private EditBox hexField;
    private float value = 1.0F;
    private boolean updatingHex;

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
        this.wheel = new ColorWheelWidget(cx, 34, WHEEL, hsv[0], hsv[1]);
        this.addRenderableWidget(this.wheel);

        this.addRenderableWidget(new AbstractSliderButton(
                w / 2 - 100, 34 + WHEEL + 8, 200, 18,
                Component.literal(brightnessLabel()), value) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal(brightnessLabel()));
            }

            @Override
            protected void applyValue() {
                value = (float) this.value;
                apply();
                syncHexField();
            }
        });
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(w / 2 - 100, this.height - 26, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            this.target.accept(originalRgb);
            this.minecraft.setScreen(parent);
        }).bounds(w / 2 + 2, this.height - 26, 98, 20).build());

        // Hex paste field: type or paste "#RRGGBB" and the wheel/preview follow.
        int fieldY = 34 + WHEEL + 30;
        this.hexField = new EditBox(this.font, w / 2 - 62, fieldY, 104, 16,
                Component.literal("Hex colour"));
        this.hexField.setMaxLength(7);
        this.hexField.setValue(String.format(Locale.ROOT, "#%06X", currentColor() & 0xFFFFFF));
        this.hexField.setResponder(text -> {
            Integer parsed = TintPalette.tryParseHex(text);
            if (parsed != null && !updatingHex) {
                float[] parsedHsv = rgbToHsv(parsed);
                this.value = parsedHsv[2];
                this.wheel.setHsv(parsedHsv[0], parsedHsv[1]);
                apply();
            }
            this.updatingHex = false;
        });
        this.addRenderableWidget(this.hexField);

        this.addRenderableOnly(new LabelWidget(0, 12, w, 14, Component.literal(this.title)));
        this.addRenderableOnly(new LabelWidget(0, fieldY + 20, w, 12,
                Component.literal("Click or drag the wheel for hue + saturation, or type a hex code")));
        this.swatchX = w / 2 + 50;
        this.swatchY = fieldY;
    }

    private int swatchX;
    private int swatchY;

    private String brightnessLabel() {
        return String.format(Locale.ROOT, "Brightness: %d%%", (int) (value * 100));
    }

    private int currentColor() {
        return wheel != null ? wheel.currentRgb(value) : originalRgb;
    }

    private void apply() {
        this.target.accept(currentColor());
    }

    /** Mirrors the live colour into the hex field unless the user is editing it. */
    private void syncHexField() {
        if (hexField != null && !hexField.isFocused()) {
            String next = String.format(Locale.ROOT, "#%06X", currentColor() & 0xFFFFFF);
            if (!next.equalsIgnoreCase(hexField.getValue())) {
                updatingHex = true;
                hexField.setValue(next);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int rgb = currentColor();
        graphics.fill(swatchX, swatchY, swatchX + 16, swatchY + 16, 0xFF000000 | rgb);
        graphics.outline(swatchX - 1, swatchY - 1, 18, 18, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
