package com.pawjwp.worldpresets.world;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.CreationPreset.RespawnMode;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Saves the dimension each player's spawnpoint was last set in to Forge's persistent data.
 * The bed only version ignores respawn anchors so the player is less likely to be stranded in the nether.
 */
@Mod.EventBusSubscriber(modid = WorldPresets.MODID)
public final class RespawnTracker {
    private static final String LAST_SPAWN = "LastSpawnDimension";
    private static final String LAST_BED = "LastBedDimension";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || event.getNewSpawn() == null) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag ours = persisted.getCompound(WorldPresets.MODID);
        String dimension = event.getSpawnLevel().location().toString();
        ours.putString(LAST_SPAWN, dimension);
        // Anything other than a respawn anchor counts the same as a bed including /spawnpoint and unreachable locations.
        ServerLevel level = player.getServer().getLevel(event.getSpawnLevel());
        boolean anchor = level != null && level.isLoaded(event.getNewSpawn())
                && level.getBlockState(event.getNewSpawn()).is(Blocks.RESPAWN_ANCHOR);
        if (!anchor) ours.putString(LAST_BED, dimension);
        persisted.put(WorldPresets.MODID, ours);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * The dimension that a player without a usable spawnpoint will respawn in.
     */
    @Nullable
    public static ServerLevel resolveRespawnLevel(ServerPlayer player, MinecraftServer server) {
        WorldSetupData data = WorldSetupData.get(server);
        if (data == null || data.respawnMode == RespawnMode.VANILLA) return null;
        ResourceKey<Level> dimension = switch (data.respawnMode) {
            case ALWAYS_SPAWN_DIMENSION -> data.spawnDimension;
            case LAST_DIMENSION -> stored(player, LAST_SPAWN, data.spawnDimension);
            case LAST_DIMENSION_NO_ANCHORS -> stored(player, LAST_BED, data.spawnDimension);
            default -> null;
        };
        return dimension == null ? null : server.getLevel(dimension);
    }

    private static ResourceKey<Level> stored(ServerPlayer player, String key, ResourceKey<Level> fallback) {
        CompoundTag ours = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(WorldPresets.MODID);
        return ours.contains(key)
                ? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(ours.getString(key)))
                : fallback;
    }
}
