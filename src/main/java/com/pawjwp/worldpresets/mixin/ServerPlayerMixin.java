package com.pawjwp.worldpresets.mixin;

import com.pawjwp.worldpresets.world.RespawnTracker;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Chooses the respawn dimension from the world's respawn mode
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
}
