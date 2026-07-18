package com.pawjwp.worldpresets.client;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.PresetManager;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldPresets.MODID, value = Dist.CLIENT)
public final class ClientEvents
{
    // Reload all presets whenever a player opens the create world screen
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event)
    {
        if (event.getNewScreen() instanceof CreateWorldScreen)
        {
            PresetManager.reload();
            PresetImages.reload();
        }
    }
}
