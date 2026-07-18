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
        int top = area.top() + 8;
        // Try to recreate the vanilla math for calculating the footer position to avoid overlapping it.
        // Can't be set statically because vanilla snaps to an even-numbered Y coordinate.
        int bottom = Math.min(area.bottom(), Mth.roundToward(this.screen.height - 36 - 2, 2));
        // Full tab width, the list is centered within it
        this.list.setBounds(area.left(), top, area.width(), bottom - top);
    }
}
