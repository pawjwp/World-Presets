package com.pawjwp.worldpresets.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/** The tab added to the create-world screen with the preset list. */
public class PresetsTab implements Tab
{
    public static final Component TITLE = Component.translatable("worldpresets.tab.presets");

    private final CreateWorldScreen screen;
    public final PresetListWidget list;

    public PresetsTab(CreateWorldScreen screen)
    {
        this.screen = screen;
        this.list = new PresetListWidget(screen);
    }

    @Override
    public Component getTabTitle()
    {
        return TITLE;
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer)
    {
        consumer.accept(this.list);
    }

    @Override
    public void doLayout(ScreenRectangle area)
    {
        this.list.setBounds(area.left(), area.top(), area.width(), area.height());
    }
}
