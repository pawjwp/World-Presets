package com.pawjwp.worldpresets.world;

import com.pawjwp.worldpresets.preset.CreationPreset.RespawnMode;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

/**
 * Saves a preset's spawn dimension override with the world, so respawns/joins continue working.
 */
public class WorldSetupData extends SavedData
{
    private static final String ID = "worldpresets_setup";

    public final ResourceKey<Level> spawnDimension;
    public final RespawnMode respawnMode;

    private WorldSetupData(ResourceKey<Level> spawnDimension, RespawnMode respawnMode)
    {
        this.spawnDimension = spawnDimension;
        this.respawnMode = respawnMode;
    }

    @Nullable
    public static WorldSetupData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().get(WorldSetupData::load, ID);
    }

    public static void create(MinecraftServer server, ResourceKey<Level> spawnDimension, RespawnMode respawnMode)
    {
        WorldSetupData data = new WorldSetupData(spawnDimension, respawnMode);
        data.setDirty();
        server.overworld().getDataStorage().set(ID, data);
    }

    private static WorldSetupData load(CompoundTag tag)
    {
        RespawnMode mode;
        try
        {
            mode = RespawnMode.byName(tag.getString("RespawnMode"));
        }
        catch (IllegalArgumentException e)
        {
            mode = RespawnMode.LAST_DIMENSION;
        }
        return new WorldSetupData(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("SpawnDimension"))), mode);
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putString("SpawnDimension", this.spawnDimension.location().toString());
        tag.putString("RespawnMode", this.respawnMode.name().toLowerCase(java.util.Locale.ROOT));
        return tag;
    }
}