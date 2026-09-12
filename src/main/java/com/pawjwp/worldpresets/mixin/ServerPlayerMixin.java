package com.pawjwp.worldpresets.mixin;

import com.pawjwp.worldpresets.world.RespawnTracker;
import com.pawjwp.worldpresets.world.WorldSetupData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Reports the respawn dimension from the world's respawn mode, places new players at the spawn if set to a precise position.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Shadow
    @Nullable
    private BlockPos respawnPosition;

    @Inject(method = "getRespawnDimension", at = @At("HEAD"), cancellable = true)
    private void worldpresets$respawnDimension(CallbackInfoReturnable<ResourceKey<Level>> cir)
    {
        if (this.respawnPosition != null) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        MinecraftServer server = self.getServer();
        if (server == null) return;
        ServerLevel level = RespawnTracker.resolveRespawnLevel(self, server);
        if (level != null) cir.setReturnValue(level.dimension());
    }

    /**
     * Places a new player at the precise world spawn, skipping the vanilla spawn radius.
     * Used if a precise placement mode (exact or clear) is set.
     */
    @Inject(method = "fudgeSpawnLocation", at = @At("HEAD"), cancellable = true)
    private void worldpresets$exactSpawn(ServerLevel level, CallbackInfo ci)
    {
        WorldSetupData data = WorldSetupData.get(level.getServer());
        if (data == null || !data.exactSpawn || level.dimension() != data.spawnDimension) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.moveTo(level.getSharedSpawnPos(), level.getSharedSpawnAngle(), 0.0F);
        ci.cancel();
    }
}
