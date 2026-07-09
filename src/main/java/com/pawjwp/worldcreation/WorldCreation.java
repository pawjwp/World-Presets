package com.pawjwp.worldcreation;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(WorldCreation.MODID)
public class WorldCreation
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "worldcreation";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldCreation()
    {
        // Most behavior will be in @Mod.EventBusSubscriber classes and mixins
    }
}
