package com.pawjwp.worldpresets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

public class PresetListWidget extends AbstractWidget {
    private final ListView list;

    public PresetListWidget(CreateWorldScreen screen) {
        super(0, 0, 0, 0, PresetsTab.TITLE);
        this.list = new ListView(screen);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.setX(x);
        this.setY(y);
        this.setWidth(width);
        this.setHeight(height);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.list.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.list.updateNarration(output);
    }

    private static class ListView extends ObjectSelectionList<ListView.Entry> {
        ListView(CreateWorldScreen screen) {
            super(Minecraft.getInstance(), 0, 0, 0, 0, 24);
        }

        private static class Entry extends ObjectSelectionList.Entry<Entry> {
            @Override
            public Component getNarration() {
                return Component.empty();
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width,
                               int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            }
        }
    }
}