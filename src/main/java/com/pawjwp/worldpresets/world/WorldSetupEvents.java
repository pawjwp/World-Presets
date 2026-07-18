package com.pawjwp.worldpresets.world;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.CreationPreset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Applies the selected preset to the created world.
 * Structures are anchored before vanilla chooses the spawn block to avoid spawning clipped in a wall.=
 */
@Mod.EventBusSubscriber(modid = WorldPresets.MODID)
public final class WorldSetupEvents
{
    /** Holds the preset until server starts, for presets whose spawn dimension doesn't exist yet. */
    @Nullable
    private static CreationPreset deferredPreset;

    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        CreationPreset preset = PendingWorldSetup.consume();
        if (preset == null) return;
        if (preset.spawnDimension() != null)
        {
            deferredPreset = preset;
            return;
        }
    }

    /**
     * Places the world's spawn point in its configured dimension.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event)
    {
        CreationPreset preset = deferredPreset;
        if (preset == null) return;
        deferredPreset = null;

        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, preset.spawnDimension()));
        ServerLevelData levelData = (ServerLevelData) server.getWorldData().overworldData();
        if (level == null)
        {
            WorldPresets.LOGGER.error("Preset spawn dimension {} does not exist, falling back to the overworld", preset.spawnDimension());
            BlockPos spawn = new BlockPos(levelData.getXSpawn(), levelData.getYSpawn(), levelData.getZSpawn());
            BlockPos safe = settleSpawn(server.overworld(), spawn);
            if (safe != null) levelData.setSpawn(safe, 0.0F);
            return;
        }

        BlockPos anchor = spawnAnchor(level);
        BlockPos spawn = settleSpawn(level, anchor);
        // World spawn coordinates are saved in the overworld's level data and are used for all dimensions
        levelData.setSpawn(spawn != null ? spawn : anchor, 0.0F);
        WorldSetupData.create(server, level.dimension(), preset.respawnMode());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        PendingWorldSetup.set(null);
        deferredPreset = null;
    }

    /** The spawn point targeted in this dimension before chunks are loaded. */
    private static BlockPos spawnAnchor(ServerLevel level)
    {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkPos chunkPos = new ChunkPos(chunkSource.randomState().sampler().findSpawnPosition());
        int y = chunkSource.getGenerator().getSpawnHeight(level);
        if (y < level.getMinBuildHeight())
        {
            BlockPos corner = chunkPos.getWorldPosition();
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE, corner.getX() + 8, corner.getZ() + 8);
        }
        return chunkPos.getWorldPosition().offset(8, y, 8);
    }

    /**
     * Recreate's vanilla's chunk spiral to locate safe spawn blocks
     */
    @Nullable
    private static BlockPos settleSpawn(ServerLevel level, BlockPos anchor)
    {
        ChunkPos center = new ChunkPos(anchor);
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        for (int i = 0; i < Mth.square(11); ++i)
        {
            if (x >= -5 && x <= 5 && z >= -5 && z <= 5)
            {
                BlockPos safe = PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(center.x + x, center.z + z));
                if (safe != null) return safe;
            }
            if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z)
            {
                int swap = dx;
                dx = -dz;
                dz = swap;
            }
            x += dx;
            z += dz;
        }
        return verticalScan(level, anchor);
    }

    /** Scans the spawn coordinate's column for solid ground with two air blocks above it. */
    @Nullable
    private static BlockPos verticalScan(ServerLevel level, BlockPos anchor)
    {
        int top = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.dimensionType().logicalHeight()) - 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(anchor.getX(), 0, anchor.getZ());
        for (int y = top; y > level.getMinBuildHeight(); --y)
        {
            if (level.getBlockState(pos.setY(y)).isAir() && level.getBlockState(pos.setY(y + 1)).isAir()
                    && !level.getBlockState(pos.setY(y - 1)).isAir())
            {
                return new BlockPos(anchor.getX(), y, anchor.getZ());
            }
        }
        return null;
    }
}
