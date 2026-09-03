package dev.jetstreams.client.gui;

import dev.jetstreams.JetStreams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * HSV colour wheel: angle = hue, radius = saturation. Click or drag inside the circle to
 * pick; the value (brightness) channel is a separate slider on the editor screen. The wheel
 * texture is generated once into a DynamicTexture and blitted every frame.
 */
public class ColorWheelWidget extends AbstractWidget {
    public static final Identifier WHEEL_TEXTURE = JetStreams.id("textures/gui/hsv_wheel.png");

    private static final int TEX = 96;
    private static boolean textureRegistered;

    private float hue;          // 0..1
    private float saturation;   // 0..1

    public ColorWheelWidget(int x, int y, int size, float hue, float saturation) {
        super(x, y, size, size, Component.literal("Colour wheel"));
        this.hue = hue;
        this.saturation = saturation;
        ensureTexture();
    }

    /** Generates the HSV wheel texture once per game session. */
    private static void ensureTexture() {
        if (textureRegistered) {
            return;
        }
        NativeImage image = new NativeImage(TEX, TEX, true);
        float radius = TEX / 2.0F;
        for (int py = 0; py < TEX; py++) {
            for (int px = 0; px < TEX; px++) {
                double dx = (px + 0.5) - radius;
                double dy = (py + 0.5) - radius;
                double r = Math.hypot(dx, dy) / (radius - 1.0);
                if (r > 1.08) {
                    continue; // outside the disc; stays transparent
                }
                float h = (float) normalizeHue(Math.atan2(dy, dx));
                float s = (float) Math.min(1.0, r);
                int rgb = hsvToRgb(h, s, 1.0F);
                int a;
                if (r <= 1.0) {
                    a = 255;
                } else {
                    a = (int) (255 * Math.max(0.0, 1.08 - r) / 0.08);
                }
                image.setPixelABGR(px, py, (a << 24) | (rgb & 0x00FFFFFF));
            }
        }
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        // (this constructor uploads immediately - no separate upload() needed)
        DynamicTexture texture = new DynamicTexture(() -> "jetstreams_hsv_wheel", image);
        tm.register(WHEEL_TEXTURE, texture);
        textureRegistered = true;
    }

    /** Standard HSV -> packed RGB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = (h - (float) Math.floor(h)) * 6.0F;
        int i = (int) h;
        float f = h - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public void setHsv(float hue, float saturation) {
        this.hue = hue;
        this.saturation = saturation;
    }

    /** Current full colour (value/brightness channel supplied by the editor screen). */
    public int currentRgb(float value) {
        return hsvToRgb(hue, saturation, value);
    }

    private void pick(double mouseX, double mouseY) {
        double cx = getX() + width / 2.0;
        double cy = getY() + height / 2.0;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double r = Math.hypot(dx, dy) / (width / 2.0);
        if (r > 1.0) {
            return;
        }
        this.hue = (float) normalizeHue(Math.atan2(dy, dx));
        this.saturation = (float) Math.min(1.0, r);
    }

    private static double normalizeHue(double atan) {
        double h = atan / (2.0 * Math.PI);
        return h - Math.floor(h);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        pick(event.x(), event.y());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        pick(event.x(), event.y());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int size = width;
        graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL_TEXTURE,
                getX(), getY(), 0.0F, 0.0F, size, size, TEX, TEX, -1);
        // Selector dot at the current hue/sat.
        double cx = getX() + width / 2.0 + Math.cos(hue * 2.0 * Math.PI) * saturation * (width / 2.0);
        double cy = getY() + height / 2.0 + Math.sin(hue * 2.0 * Math.PI) * saturation * (height / 2.0);
        int d = 4;
        graphics.fill((int) cx - d, (int) cy - d, (int) cx + d, (int) cy + d, 0xFFFFFFFF);
        graphics.fill((int) cx - d + 1, (int) cy - d + 1, (int) cx + d - 1, (int) cy + d - 1, 0xFF000000);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // narration handled by the editor screen label
    }
}
