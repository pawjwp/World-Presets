package com.pawjwp.worldpresets.world;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.mixin.StructureManagerAccessor;
import com.pawjwp.worldpresets.preset.CreationPreset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates a preset's structures through vanilla's full structure generation system.
 * StructureStarts are assembled immediately and without biome checking.
 * After that, they are passed to ChunkGenerator's createStructures as the chunks are force-generated.
 * Everything else like terrain adaptation works like it does in vanilla.
 */
public final class StructurePlacer
{
    /** An assembled structure starts in a pending state until claimed by chunk generation. */
    private static final Map<ResourceKey<Level>, Map<Long, List<StructureStart>>> PENDING = new ConcurrentHashMap<>();

    public static void placeAll(ServerLevel level, List<CreationPreset.StructureSpec> specs, BlockPos anchor)
    {
        if (specs.isEmpty()) return;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkGenerator generator = level.getChunkSource().getGenerator();

        Map<Long, List<StructureStart>> pending = new ConcurrentHashMap<>();
        List<StructureStart> starts = new ArrayList<>();
        for (CreationPreset.StructureSpec spec : specs)
        {
            var holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, spec.structure()));
            if (holder.isEmpty())
            {
                WorldPresets.LOGGER.error("Unknown starting structure {}, skipping generation", spec.structure());
                continue;
            }
            ChunkPos chunkPos = new ChunkPos(anchor.offset(spec.offsetX(), 0, spec.offsetZ()));
            StructureStart start = holder.get().value().generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                    chunkPos, 0, level, biome -> true);
            if (!start.isValid())
            {
                WorldPresets.LOGGER.error("Starting structure {} failed to generate a valid start at {}", spec.structure(), chunkPos.getWorldPosition());
                continue;
            }
            pending.computeIfAbsent(chunkPos.toLong(), k -> new ArrayList<>()).add(start);
            starts.add(start);
        }
        if (starts.isEmpty()) return;

        PENDING.put(level.dimension(), pending);
        // Generate chunks to let ChunkGeneratorMixin use the starts in the STRUCTURE_STARTS stage
        for (StructureStart start : starts)
        {
            BoundingBox box = start.getBoundingBox();
            ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
            ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
            ChunkPos.rangeClosed(min, max).forEach(chunkPos -> level.getChunk(chunkPos.x, chunkPos.z));
            WorldPresets.LOGGER.info("Generated starting structure {} at {}", start.getStructure(), start.getChunkPos().getWorldPosition());
        }
        PENDING.remove(level.dimension());
    }

    /** Passes this chunk's pending structure starts to its structure manager. */
    public static void injectPendingStarts(StructureManager structureManager, ChunkAccess chunk)
    {
        if (PENDING.isEmpty()) return;
        if (!(((StructureManagerAccessor) structureManager).worldpresets$getLevel() instanceof WorldGenLevel level)) return;
        Map<Long, List<StructureStart>> pending = PENDING.get(level.getLevel().dimension());
        if (pending == null) return;
        List<StructureStart> starts = pending.remove(chunk.getPos().toLong());
        if (starts == null) return;
        SectionPos section = SectionPos.bottomOf(chunk);
        for (StructureStart start : starts)
        {
            structureManager.setStartForStructure(section, start.getStructure(), start, chunk);
        }
    }
}
