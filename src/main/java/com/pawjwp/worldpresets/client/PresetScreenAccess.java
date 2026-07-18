package com.pawjwp.worldpresets.client;

import com.pawjwp.worldpresets.preset.CreationPreset;

import javax.annotation.Nullable;

/** Exposes the preset-selection state that CreateWorldScreenMixin adds to CreateWorldScreen. */
public interface PresetScreenAccess
{
    void worldpresets$applyPreset(CreationPreset preset);

    @Nullable
    CreationPreset worldpresets$selectedPreset();
}