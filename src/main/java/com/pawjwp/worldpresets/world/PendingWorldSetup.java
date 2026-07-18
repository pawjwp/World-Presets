package com.pawjwp.worldpresets.world;

import com.pawjwp.worldpresets.preset.CreationPreset;

import javax.annotation.Nullable;

/**
 * Sends the chosen preset to the integrated server when starting.
 */
public final class PendingWorldSetup
{
    @Nullable
    private static CreationPreset pending;

    public static void set(@Nullable CreationPreset preset)
    {
        pending = preset;
    }

    @Nullable
    public static CreationPreset consume()
    {
        CreationPreset preset = pending;
        pending = null;
        return preset;
    }
}