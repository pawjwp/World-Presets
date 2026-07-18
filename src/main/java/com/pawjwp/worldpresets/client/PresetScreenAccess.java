package com.pawjwp.worldpresets.client;

import com.pawjwp.worldpresets.preset.CreationPreset;

import javax.annotation.Nullable;

/** Exposes the preset-selection state that CreateWorldScreenMixin adds to CreateWorldScreen. */
public interface PresetScreenAccess
{
    void worldpresets$applyPreset(CreationPreset preset);

    @Nullable
    CreationPreset worldpresets$selectedPreset();

    /** The preset list's scroll offset */
    double worldpresets$listScroll();

    void worldpresets$setListScroll(double scroll);

    /** Requests that the preset list re-focus the selected item. */
    void worldpresets$requestFocusRestore();

    /** Returns true the first time it is called after a focus restore, then false until the next restore. */
    boolean worldpresets$consumeFocusRestore();
}