package com.pawjwp.worldpresets;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(WorldPresets.MODID)
public class WorldPresets
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "worldpresets";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldPresets()
    {
        // Most behavior will be in @Mod.EventBusSubscriber classes and mixins
    }
}
