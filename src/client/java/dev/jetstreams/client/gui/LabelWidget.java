package dev.jetstreams.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Minimal centered text label for screens (26.1 removed concrete string widgets). */
public class LabelWidget extends AbstractWidget {
    public LabelWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        Component message = this.getMessage();
        int textWidth = font.width(message.getVisualOrderText());
        int x = this.getX() + Math.max(0, (this.width - textWidth) / 2);
        int y = this.getY() + Math.max(0, (this.height - font.lineHeight) / 2);
        graphics.text(font, message, x, y, 0xFFFFFFFF, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // static label - no narration needed
    }
}
