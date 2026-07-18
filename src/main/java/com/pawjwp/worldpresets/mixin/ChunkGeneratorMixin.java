package com.pawjwp.worldpresets.mixin;

import com.pawjwp.worldpresets.world.StructurePlacer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a preset's StructureStarts to world generation while
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin
{
    @Inject(method = "createStructures", at = @At("TAIL"))
    private void worldpresets$injectPendingStarts(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
                                                   StructureManager structureManager, ChunkAccess chunk,
                                                   StructureTemplateManager templateManager, CallbackInfo ci)
    {
        StructurePlacer.injectPendingStarts(structureManager, chunk);
    }
}
