package com.pawjwp.worldpresets.world;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.CreationPreset;
import com.pawjwp.worldpresets.preset.CreationPreset.Placement;
import com.pawjwp.worldpresets.preset.CreationPreset.StartPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Applies the selected preset to the created world.
 * Structures are anchored before vanilla chooses the spawn block to avoid spawning clipped in a wall.
 */
@Mod.EventBusSubscriber(modid = WorldPresets.MODID)
public final class WorldSetupEvents
{
    /** Holds the preset until its spawn dimension loads, which happens after the overworld picks its spawn. */
    @Nullable
    private static CreationPreset deferredPreset;

    /**
     * Places the preset's structures at spawn before vanilla generates the area.
     * Defers to onLevelLoad if the preset uses a spawn dimension other than the overworld.
     * Fires once when the new world picks its spawn.
     */
    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        CreationPreset preset = PendingWorldSetup.consume();
        if (preset == null) return;
        ResourceLocation dimension = preset.spawnDimension();
        if (dimension != null && !level.registryAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(dimension))
        {
            WorldPresets.LOGGER.error("Preset spawn dimension {} does not exist, falling back to the overworld", dimension);
            dimension = Level.OVERWORLD.location();
        }
        if (dimension != null && !dimension.equals(Level.OVERWORLD.location()))
        {
            deferredPreset = preset;
            return;
        }
        StartPosition start = preset.startPosition();
        boolean exactSpawn = start != null && start.exact();
        // Save the setup when respawn behavior changes or an exact spawn must skip vanilla's surface fudge
        if (dimension != null || exactSpawn)
            WorldSetupData.create(level.getServer(), Level.OVERWORLD, preset.respawnMode(), null, exactSpawn);
        if (start == null)
        {
            // Anchor structures on the same area vanilla is about to choose, then let vanilla settle the spawn
            StructurePlacer.placeAll(level, preset.structures(), spawnAnchor(level));
            return;
        }
        // Take over the overworld spawn: apply the override and skip vanilla's search
        BlockPos spawn = applyStartPosition(level, start, preset.structures());
        event.getSettings().setSpawn(spawn, 0.0F);
        // Vanilla's own bonus chest is skipped along with its spawn search, so place it at the chosen spawn
        if (level.getServer().getWorldData().worldGenOptions().generateBonusChest())
            placeBonusChest(level, spawn);
        event.setCanceled(true);
    }

    /**
     * Places the world's spawnpoint and structures in the preset's spawn dimension once it loads.
     * Runs while the server is creating levels and before the start region generates.
     * MinecraftServerMixin prepares and keeps loaded the chunks around the spawn set here.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        CreationPreset preset = deferredPreset;
        if (preset == null || !(event.getLevel() instanceof ServerLevel level) || !level.dimension().location().equals(preset.spawnDimension())) return;
        deferredPreset = null;

        StartPosition start = preset.startPosition();
        BlockPos spawn;
        if (start == null)
        {
            BlockPos anchor = spawnAnchor(level);
            StructurePlacer.placeAll(level, preset.structures(), anchor);
            BlockPos settled = settleSpawn(level, anchor);
            spawn = settled != null ? settled : anchor;
        }
        else
        {
            spawn = applyStartPosition(level, start, preset.structures());
        }
        // World spawn coordinates are saved in the overworld's level data and are used for all dimensions
        ServerLevelData levelData = (ServerLevelData) level.getServer().getWorldData().overworldData();
        // When both dimensions are loaded, keep the overworld's spawn before it's overwritten so its chunks can stay loaded too
        BlockPos overworldSpawn = preset.keepLoaded() == CreationPreset.SpawnChunkLoading.BOTH
                ? new BlockPos(levelData.getXSpawn(), levelData.getYSpawn(), levelData.getZSpawn())
                : null;
        levelData.setSpawn(spawn, 0.0F);
        WorldSetupData.create(level.getServer(), level.dimension(), preset.respawnMode(), overworldSpawn, start != null && start.exact());
    }

    /**
     * Keeps the overworld's original spawn chunks loaded alongside the spawn dimension when the preset requests both.
     * The start region is already kept loaded by the redirects in MinecraftServerMixin.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        WorldSetupData data = WorldSetupData.get(event.getServer());
        if (data == null || data.overworldSpawn == null) return;
        ServerLevel overworld = event.getServer().overworld();
        overworld.getChunkSource().addRegionTicket(TicketType.START, new ChunkPos(data.overworldSpawn), 11, Unit.INSTANCE);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        PendingWorldSetup.set(null);
        deferredPreset = null;
    }

    /**
     * Resolves the world spawn from a preset's start position and places its structures
     */
    private static BlockPos applyStartPosition(ServerLevel level, StartPosition start, List<CreationPreset.StructureSpec> structures)
    {
        BlockPos base = start.anyRelative() ? predictedSpawnLocation(level) : BlockPos.ZERO;
        int x = start.x().resolve(base.getX());
        int y = start.y().resolve(base.getY());
        int z = start.z().resolve(base.getZ());

        BlockPos anchor = new BlockPos(x, y, z);
        if (start.placement() == Placement.FIND_CLIMATE)
        {
            BlockPos climate = ClimateSpawnFinder.find(level, x, z);
            // Fallback if the climate spawn finder fails to find a target
            if (climate != null) anchor = new BlockPos(climate.getX(), y, climate.getZ());
        }

        StructurePlacer.placeAll(level, structures, anchor);

        switch (start.placement())
        {
            case EXACT -> { return clampToWorld(level, anchor); }
            case CLEAR ->
            {
                BlockPos spawn = clampToWorld(level, anchor);
                carvePocket(level, spawn);
                return spawn;
            }
            default ->
            {
                BlockPos safe = settleSpawn(level, anchor);
                // If there are no safe surfaces nearby, fall back to spawn height
                return safe != null ? safe : spawnHeightAt(level, anchor);
            }
        }
    }

    /** The generator's spawn height when no safe ground is found. */
    private static BlockPos spawnHeightAt(ServerLevel level, BlockPos pos)
    {
        int y = level.getChunkSource().getGenerator().getSpawnHeight(level);
        if (y < level.getMinBuildHeight()) y = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    /** Places vanilla's bonus chest */
    private static void placeBonusChest(ServerLevel level, BlockPos spawn)
    {
        level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolder(MiscOverworldFeatures.BONUS_CHEST)
                .ifPresent(feature -> feature.value().place(level, level.getChunkSource().getGenerator(), level.random, spawn));
    }

    /** The block the dimension's terrain is built from, used to add a floor block to an unsafe spawn. */
    private static BlockState fillBlock(ServerLevel level)
    {
        return level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise
                ? noise.generatorSettings().value().defaultBlock()
                : Blocks.STONE.defaultBlockState();
    }

    /** The spawn location that vanilla's search would choose in this dimension. */
    private static BlockPos predictedSpawnLocation(ServerLevel level)
    {
        BlockPos anchor = spawnAnchor(level);
        BlockPos settled = settleSpawn(level, anchor);
        return settled != null ? settled : anchor;
    }

    /** Ensures that the exact spawn Y is in the dimension's build range. */
    private static BlockPos clampToWorld(ServerLevel level, BlockPos pos)
    {
        int y = Mth.clamp(pos.getY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        if (y == pos.getY()) return pos;
        WorldPresets.LOGGER.warn("start_position Y {} is outside the build range, clamped to {}", pos.getY(), y);
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    /** Clears space for the player to avoid suffocation and adds a floor block if needed at the spawn location. */
    private static void carvePocket(ServerLevel level, BlockPos feet)
    {
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
        BlockPos below = feet.below();
        if (below.getY() >= level.getMinBuildHeight() && !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
        {
            level.setBlockAndUpdate(below, fillBlock(level));
        }
    }

    /** The spawn location targeted in this dimension before chunks are loaded. */
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
     * Recreates vanilla's chunk spiral to locate safe spawn blocks.
     */
    @Nullable
    private static BlockPos settleSpawn(ServerLevel level, BlockPos anchor)
    {
        boolean ceiling = level.dimensionType().hasCeiling();
        ChunkPos center = new ChunkPos(anchor);
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        for (int i = 0; i < Mth.square(11); ++i)
        {
            if (x >= -5 && x <= 5 && z >= -5 && z <= 5)
            {
                ChunkPos chunkPos = new ChunkPos(center.x + x, center.z + z);
                BlockPos safe = ceiling ? ceilingSpawnPosInChunk(level, chunkPos) : PlayerRespawnLogic.getSpawnPosInChunk(level, chunkPos);
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

    /**
     * Finds a valid spawn location in a given chunk for dimensions with a ceiling.
     */
    @Nullable
    private static BlockPos ceilingSpawnPosInChunk(ServerLevel level, ChunkPos chunkPos)
    {
        // Each column is scanned down from the generator's spawn height, looking for solid ground with two air blocks above it.
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int top = level.getChunkSource().getGenerator().getSpawnHeight(level);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); ++x)
        {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); ++z)
            {
                for (int y = top; y > level.getMinBuildHeight(); --y)
                {
                    if (chunk.getBlockState(pos.set(x, y, z)).isAir() && chunk.getBlockState(pos.set(x, y + 1, z)).isAir()
                            && Block.isFaceFull(chunk.getBlockState(pos.set(x, y - 1, z)).getCollisionShape(level, pos), Direction.UP))
                    {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
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
                    && Block.isFaceFull(level.getBlockState(pos.setY(y - 1)).getCollisionShape(level, pos), Direction.UP))
            {
                return new BlockPos(anchor.getX(), y, anchor.getZ());
            }
        }
        return null;
    }
}
