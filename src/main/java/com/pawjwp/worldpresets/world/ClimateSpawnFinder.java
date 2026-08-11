package com.pawjwp.worldpresets.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A recreation of Vanilla's Climate.SpawnFinder, with an origin point at the chosen coordinate instead of the world center.
 * Finds a suitable climate near that center and returns null if there are no valid targets.
 */
public final class ClimateSpawnFinder
{
    @Nullable
    public static BlockPos find(ServerLevel level, int centerX, int centerZ)
    {
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        List<Climate.ParameterPoint> spawnTarget = sampler.spawnTarget();
        if (spawnTarget.isEmpty()) return null;

        Result best = score(sampler, spawnTarget, centerX, centerZ, centerX, centerZ);
        best = radialSearch(sampler, spawnTarget, centerX, centerZ, best, 2048.0F, 512.0F);
        best = radialSearch(sampler, spawnTarget, centerX, centerZ, best, 512.0F, 32.0F);
        return best.pos;
    }

    /** Searches in a spiral outward from the current best, keeping whichever spot scores lowest. */
    private static Result radialSearch(Climate.Sampler sampler, List<Climate.ParameterPoint> spawnTarget,
                                       int centerX, int centerZ, Result best, float maxRadius, float step)
    {
        float angle = 0.0F;
        float radius = step;
        BlockPos around = best.pos;
        while (radius <= maxRadius)
        {
            int x = around.getX() + (int) (Math.sin(angle) * radius);
            int z = around.getZ() + (int) (Math.cos(angle) * radius);
            Result candidate = score(sampler, spawnTarget, x, z, centerX, centerZ);
            if (candidate.fitness < best.fitness) best = candidate;
            angle += step / radius;
            if (angle > Math.PI * 2) { angle = 0.0F; radius += step; }
        }
        return best;
    }

    /**
     * Calculates the score for a column including the distance penalty, amd climate difference from the spawn target.
     */
    private static Result score(Climate.Sampler sampler, List<Climate.ParameterPoint> spawnTarget,
                                int x, int z, int centerX, int centerZ)
    {
        long dx = x - centerX;
        long dz = z - centerZ;
        long distancePenalty = (long) ((double) Mth.square(10000.0F) * Math.pow((double) (Mth.square(dx) + Mth.square(dz)) / Mth.square(2500.0D), 2.0D));
        // Depth is 0
        Climate.TargetPoint sampled = sampler.sample(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z));
        Climate.TargetPoint point = new Climate.TargetPoint(sampled.temperature(), sampled.humidity(), sampled.continentalness(), sampled.erosion(), 0L, sampled.weirdness());
        long climateFitness = Long.MAX_VALUE;
        for (Climate.ParameterPoint target : spawnTarget)
        {
            climateFitness = Math.min(climateFitness, fitness(target, point));
        }
        return new Result(new BlockPos(x, 0, z), distancePenalty + climateFitness);
    }

    /** Recreation of Climate.ParameterPoint.fitness. */
    private static long fitness(Climate.ParameterPoint p, Climate.TargetPoint t)
    {
        return Mth.square(p.temperature().distance(t.temperature()))
                + Mth.square(p.humidity().distance(t.humidity()))
                + Mth.square(p.continentalness().distance(t.continentalness()))
                + Mth.square(p.erosion().distance(t.erosion()))
                + Mth.square(p.depth().distance(t.depth()))
                + Mth.square(p.weirdness().distance(t.weirdness()))
                + Mth.square(p.offset());
    }

    private record Result(BlockPos pos, long fitness) {}
}
