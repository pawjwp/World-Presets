package com.pawjwp.worldpresets.world;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.CreationPreset.RespawnMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
    /** The overworld's original spawn to also keep loaded, null if only the spawn dimension's chunks are loaded. */
    @Nullable
    public final BlockPos overworldSpawn;
    /** True when the start_position uses exact or clear placement, so new players skip vanilla's surface fudge. */
    public final boolean exactSpawn;

    private WorldSetupData(ResourceKey<Level> spawnDimension, RespawnMode respawnMode, @Nullable BlockPos overworldSpawn, boolean exactSpawn)
    {
        this.spawnDimension = spawnDimension;
        this.respawnMode = respawnMode;
        this.overworldSpawn = overworldSpawn;
        this.exactSpawn = exactSpawn;
    }

    @Nullable
    public static WorldSetupData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().get(WorldSetupData::load, ID);
    }

    public static void create(MinecraftServer server, ResourceKey<Level> spawnDimension, RespawnMode respawnMode, @Nullable BlockPos overworldSpawn, boolean exactSpawn)
    {
        WorldSetupData data = new WorldSetupData(spawnDimension, respawnMode, overworldSpawn, exactSpawn);
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
            WorldPresets.LOGGER.error("Unknown respawn mode '{}' in saved world data, using {}", tag.getString("RespawnMode"), RespawnMode.LAST_DIMENSION);
            mode = RespawnMode.LAST_DIMENSION;
        }
        BlockPos overworldSpawn = tag.contains("OverworldSpawn") ? NbtUtils.readBlockPos(tag.getCompound("OverworldSpawn")) : null;
        return new WorldSetupData(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("SpawnDimension"))), mode, overworldSpawn, tag.getBoolean("ExactSpawn"));
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putString("SpawnDimension", this.spawnDimension.location().toString());
        tag.putString("RespawnMode", this.respawnMode.name().toLowerCase(java.util.Locale.ROOT));
        if (this.overworldSpawn != null) tag.put("OverworldSpawn", NbtUtils.writeBlockPos(this.overworldSpawn));
        if (this.exactSpawn) tag.putBoolean("ExactSpawn", true);
        return tag;
    }
}