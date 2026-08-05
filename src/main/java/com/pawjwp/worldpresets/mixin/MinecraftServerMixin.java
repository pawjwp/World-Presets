package com.pawjwp.worldpresets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pawjwp.worldpresets.world.WorldSetupData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Generates the starting region in the preset's spawn dimension instead of the overworld
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin
{
    @WrapOperation(method = "prepareLevels",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel worldpresets$startRegionLevel(MinecraftServer server, Operation<ServerLevel> original)
    {
        WorldSetupData data = WorldSetupData.get(server);
        ServerLevel level = data == null ? null : server.getLevel(data.spawnDimension);
        return level != null ? level : original.call(server);
    }
}