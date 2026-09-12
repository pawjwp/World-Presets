package com.pawjwp.worldpresets.client;

import com.pawjwp.worldpresets.preset.CreationPreset;
import com.pawjwp.worldpresets.preset.PresetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The preset chooser shown in the create-world screen's Presets tab.
 * Uses an {@link ObjectSelectionList} wrapped in an {@link AbstractWidget} so
 * it can be used as a tab.
 */
public class PresetListWidget extends AbstractWidget implements ContainerEventHandler {
    private static final int IMAGE_WIDTH = 96;
    private static final int IMAGE_HEIGHT = 64;
    private static final int TEXT_GAP = 4;
    private static final int ROW_WIDTH = 320; // total width of one row, including image, text, and side padding
    private static final int MARGIN = 4; // top/bottom margin at the beginning and end of the list
    private static final int SELECT_PAD = 3; // gap from the image to the selection box's outer edge (2px fill plus a 1px border)
    private static final int ROW_GAP = 2; // vertical space below each row's selection box before the next row begins
    private static final int ROW_HEIGHT = IMAGE_HEIGHT + 2 * SELECT_PAD + ROW_GAP; // height of one row, including the gap below it
    private static final int SCROLLBAR_WIDTH = 6; // width of the scrollbar
    private static final int SCROLLBAR_GAP = 9; // gap from the selection's right edge to the scrollbar

    private final ListView list;
    private final List<GuiEventListener> children;
    @Nullable
    private GuiEventListener focusedChild;
    private boolean dragging;

    public PresetListWidget(CreateWorldScreen screen) {
        super(0, 0, 0, 0, PresetsTab.TITLE);
        this.list = new ListView(screen);
        this.children = List.of(this.list);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.setX(x);
        this.setY(y);
        this.width = width;
        this.height = height;
        this.list.updateSize(width, height, y, y + height);
        this.list.setLeftPos(x);
        this.list.restoreAfterApply(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.list.render(graphics, mouseX, mouseY, partialTick);
    }

    // These functions come from ContainerEventHandler and allow keyboard navigation

    @Override
    public List<GuiEventListener> children() {
        return this.children;
    }

    @Override
    public boolean isDragging() {
        return this.dragging;
    }

    @Override
    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return this.focusedChild;
    }

    @Override
    public void setFocused(@Nullable GuiEventListener child) {
        if (this.focusedChild != null)
            this.focusedChild.setFocused(false);
        if (child != null)
            child.setFocused(true);
        this.focusedChild = child;
    }

    @Override
    public void setFocused(boolean focused) {
        // Pass the focus state to the list without resetting which item is selected
        if (this.focusedChild != null)
            this.focusedChild.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return ContainerEventHandler.super.isFocused();
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        // Send focus navigation into the list itself
        if (!this.active || !this.visible)
            return null;
        return ContainerEventHandler.super.nextFocusPath(event);
    }

    // Send mouse events to the list

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return ContainerEventHandler.super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return ContainerEventHandler.super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return ContainerEventHandler.super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.list.updateNarration(output);
    }

    // Vanilla selection list, providing basic navigation features
    private class ListView extends ObjectSelectionList<ListView.Entry> {
        private final CreateWorldScreen screen;

        ListView(CreateWorldScreen screen) {
            super(Minecraft.getInstance(), 0, 0, 0, 0, ROW_HEIGHT);
            this.screen = screen;
            this.setRenderTopAndBottom(false);
            this.setRenderHeader(true, MARGIN - 4);
            CreationPreset selected = ((PresetScreenAccess) screen).worldpresets$selectedPreset();
            for (CreationPreset preset : PresetManager.getPresets()) {
                Entry entry = new Entry(preset);
                this.addEntry(entry);
                if (selected != null && selected.id().equals(preset.id()))
                    this.setSelected(entry);
            }
        }

        @Override
        public int getRowWidth() {
            // ROW_WIDTH, capped to the list width minus SCROLLBAR_GAP and SCROLLBAR_WIDTH on each side.
            return Math.min(ROW_WIDTH, this.width - 2 * (SCROLLBAR_GAP + SCROLLBAR_WIDTH));
        }

        @Override
        public int getRowLeft() {
            // The centered row's left edge, plus SELECT_PAD to reach the image's left edge.
            return this.x0 + (this.width - this.getRowWidth()) / 2 + SELECT_PAD;
        }

        @Override
        protected int getScrollbarPosition() {
            // The centered row's right edge, plus SCROLLBAR_GAP to sit the scrollbar just past it.
            return this.x0 + (this.width + this.getRowWidth()) / 2 + SCROLLBAR_GAP;
        }

        @Override
        protected int getMaxPosition() {
            // Extends the scroll range by the MARGIN minus the ROW_GAP which is already included.
            return super.getMaxPosition() + (MARGIN - ROW_GAP);
        }

        @Override
        protected void renderSelection(GuiGraphics graphics, int top, int rowWidth, int rowHeight, int outer,
                                       int inner) {
            // Draws the selection box outside the image on all four sides.
            // SELECT_PAD determines the pixel distance from the inner content (including the 1px outline of the selection box).
            int selBottom = top + IMAGE_HEIGHT + 2 * SELECT_PAD;
            int left = this.x0 + (this.width - rowWidth) / 2;
            int right = left + rowWidth;
            graphics.fill(left, top, right, selBottom, outer);
            graphics.fill(left + 1, top + 1, right - 1, selBottom - 1, inner);
        }

        // Restores the scroll position and focus saved when a preset was applied.
        // Only used with apply(), window changes don't save/restore.
        void restoreAfterApply(PresetListWidget wrapper) {
            if (!((PresetScreenAccess) this.screen).worldpresets$consumeRestore()) return;
            this.setScrollAmount(((PresetScreenAccess) this.screen).worldpresets$listScroll());
            Entry selected = this.getSelected();
            if (selected == null) return;
            this.screen.setFocused(wrapper);
            wrapper.setFocused(this);
            this.setFocused(selected);
        }

        @Override
        protected void renderList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderList(graphics, mouseX, mouseY, partialTick);
            // The gradient on the top and bottom of the list
            graphics.fillGradient(this.x0, this.y0, this.x1, this.y0 + 4, 0xFF000000, 0x00000000);
            graphics.fillGradient(this.x0, this.y1 - 4, this.x1, this.y1, 0x00000000, 0xFF000000);
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            // Controls for line spacing
            private static final int DESC_LINE_HEIGHT = 10;
            private static final int DESC_TOP = 13; // offset from imageTop to the first description line
            private static final int DESC_MAX_LINES = (IMAGE_HEIGHT - DESC_TOP - 9) / DESC_LINE_HEIGHT + 1;

            private final CreationPreset preset;
            private final PresetImages.Image image;
            private final int cropU, cropV, cropW, cropH;
            private final Font font = Minecraft.getInstance().font;

            // Title and description are placed once and cached until the text width is changed
            private int cachedTextWidth = -1;
            private FormattedCharSequence title;
            private MultiLineLabel description;

            Entry(CreationPreset preset) {
                this.preset = preset;
                this.image = PresetImages.get(preset);
                // Center-crop the source image to the 3:2 frame
                float target = (float) IMAGE_WIDTH / IMAGE_HEIGHT;
                int w = this.image.width();
                int h = this.image.height();
                if ((float) w / h > target) {
                    this.cropH = h;
                    this.cropW = Math.round(h * target);
                    this.cropU = (w - this.cropW) / 2;
                    this.cropV = 0;
                } else {
                    this.cropW = w;
                    this.cropH = Math.round(w / target);
                    this.cropU = 0;
                    this.cropV = (h - this.cropH) / 2;
                }
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width,
                               int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                // Add the select padding above the image
                int imageTop = top + SELECT_PAD;
                graphics.blit(this.image.texture(), left, imageTop, IMAGE_WIDTH, IMAGE_HEIGHT,
                        (float) this.cropU, (float) this.cropV, this.cropW, this.cropH,
                        this.image.width(), this.image.height());

                int textX = left + IMAGE_WIDTH + TEXT_GAP;
                int textWidth = width - IMAGE_WIDTH - TEXT_GAP - 2 * SELECT_PAD;
                if (textWidth != this.cachedTextWidth) {
                    this.cachedTextWidth = textWidth;
                    this.title = this.truncate(this.preset.title(), textWidth);
                    this.description = MultiLineLabel.create(this.font, this.preset.description(), textWidth,
                            DESC_MAX_LINES);
                }
                graphics.drawString(this.font, this.title, textX, imageTop + 1, 0xFFFFFF);
                this.description.renderLeftAligned(graphics, textX, imageTop + DESC_TOP, DESC_LINE_HEIGHT, 0xA0A0A0);
            }

            private FormattedCharSequence truncate(Component text, int maxWidth) {
                // Forge's ellipsize adds "..." when the text is too wide.
                return Language.getInstance().getVisualOrder(this.font.ellipsize(text, maxWidth));
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0)
                    return false;
                ListView.this.setSelected(this);
                this.apply();
                return true;
            }

            // Applies this row's preset when Enter or Space is pressed while it's focused.
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (!CommonInputs.selected(keyCode))
                    return false;
                this.apply();
                return true;
            }

            // Plays the click sound, saves the scroll position and focus, then applies the preset, which rebuilds the screen.
            void apply() {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                // All widgets are rebuilt after applying, so the scroll position and focus are saved first, and the apply is deferred until the current input is handled.
                PresetScreenAccess access = (PresetScreenAccess) ListView.this.screen;
                access.worldpresets$setListScroll(ListView.this.getScrollAmount());
                access.worldpresets$requestRestore();
                Minecraft.getInstance().tell(() -> access.worldpresets$applyPreset(this.preset));
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", this.preset.title());
            }
        }
    }
}