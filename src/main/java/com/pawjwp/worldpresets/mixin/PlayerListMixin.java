package com.pawjwp.worldpresets.mixin;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.CreationPreset;
import com.pawjwp.worldpresets.world.PendingWorldSetup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin
{
    @Shadow
    @Final
    private MinecraftServer server;

    @Unique
    private boolean worldpresets$newPlayer;

    @Unique
    private boolean worldpresets$newPlayerRedirected;

    /**
     * Detects whether the placed player is new by checking if the player's compoundtag is null.
     */
    @ModifyVariable(method = "placeNewPlayer", at = @At("STORE"), ordinal = 0)
    private CompoundTag worldpresets$captureNewPlayer(CompoundTag compoundtag)
    {
        this.worldpresets$newPlayer = compoundtag == null;
        return compoundtag;
    }

    /**
     * Places a new player in the preset's spawn dimension instead of the overworld.
     */
    @ModifyVariable(method = "placeNewPlayer", at = @At("STORE"), ordinal = 0)
    private ResourceKey<Level> worldpresets$newPlayerDimension(ResourceKey<Level> dimension)
    {
        this.worldpresets$newPlayerRedirected = false;
        if (!this.worldpresets$newPlayer) return dimension;
        CreationPreset preset = PendingWorldSetup.consume();
        if (preset == null || preset.spawnDimension() == null) return dimension;
        ResourceKey<Level> target = ResourceKey.create(Registries.DIMENSION, preset.spawnDimension());
        if (this.server.getLevel(target) == null)
        {
            WorldPresets.LOGGER.warn("Preset spawn dimension {} does not exist, using {}", preset.spawnDimension(), dimension.location());
            return dimension;
        }
        this.worldpresets$newPlayerRedirected = true;
        return target;
    }

    /**
     * Re-run player-placement logic against the dimension the player actually ends up in.
     */
    @Inject(method = "placeNewPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setServerLevel(Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER))
    private void worldpresets$repositionNewPlayer(Connection connection, ServerPlayer player, CallbackInfo ci)
    {
        if (this.worldpresets$newPlayerRedirected)
        {
            this.worldpresets$newPlayerRedirected = false;
            ((ServerPlayerAccessor) player).worldpresets$fudgeSpawnLocation(player.serverLevel());
        }
    }
}