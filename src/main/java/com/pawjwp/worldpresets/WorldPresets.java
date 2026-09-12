package com.pawjwp.worldpresets;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(WorldPresets.MODID)
public class WorldPresets {
    public static final String MODID = "worldpresets";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldPresets() {
        // Most behavior will be in @Mod.EventBusSubscriber classes and mixins
    }
}
