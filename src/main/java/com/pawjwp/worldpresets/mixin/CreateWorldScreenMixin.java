package com.pawjwp.worldpresets.mixin;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.client.PresetScreenAccess;
import com.pawjwp.worldpresets.client.PresetsTab;
import com.pawjwp.worldpresets.preset.CreationPreset;
import com.pawjwp.worldpresets.preset.PresetManager;
import com.pawjwp.worldpresets.world.PendingWorldSetup;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.GameRules;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen implements PresetScreenAccess
{
    @Shadow
    @Final
    WorldCreationUiState uiState;

    @Unique
    @Nullable
    private CreationPreset worldpresets$selected;

    @Unique
    private double worldpresets$listScroll;

    @Unique
    private boolean worldpresets$restoreFocus;

    protected CreateWorldScreenMixin(Component title)
    {
        super(title);
    }

    /** Sends the selected preset to the server that is about to start when the Create button is pressed. */
    @Inject(method = "onCreate", at = @At("HEAD"))
    private void worldpresets$captureSetup(CallbackInfo ci)
    {
        PendingWorldSetup.set(this.worldpresets$selected);
    }

    /** Adds the Presets tab to the start of the Create World screen (when a valid presets is registered). */
    @ModifyArg(method = "init",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"))
    private Tab[] worldpresets$addPresetsTab(Tab[] tabs)
    {
        if (PresetManager.getPresets().isEmpty()) return tabs;
        Tab[] withPresets = new Tab[tabs.length + 1];
        withPresets[0] = new PresetsTab((CreateWorldScreen) (Object) this);
        System.arraycopy(tabs, 0, withPresets, 1, tabs.length);
        return withPresets;
    }

    @Override
    public void worldpresets$applyPreset(CreationPreset preset)
    {
        this.worldpresets$selected = preset;
        if (preset.worldName() != null) this.uiState.setName(preset.worldName());
        if (preset.gameMode() != null) this.uiState.setGameMode(switch (preset.gameMode())
        {
            case SURVIVAL -> WorldCreationUiState.SelectedGameMode.SURVIVAL;
            case HARDCORE -> WorldCreationUiState.SelectedGameMode.HARDCORE;
            case CREATIVE -> WorldCreationUiState.SelectedGameMode.CREATIVE;
        });
        if (preset.difficulty() != null) this.uiState.setDifficulty(preset.difficulty());
        if (preset.allowCheats() != null) this.uiState.setAllowCheats(preset.allowCheats());
        if (preset.seed() != null) this.uiState.setSeed(preset.seed());
        if (preset.worldType() != null) this.worldpresets$applyWorldType(preset.worldType());
        if (!preset.gameRules().isEmpty()) this.uiState.setGameRules(worldpresets$buildGameRules(preset));
        // Rebuilds all widgets
        this.rebuildWidgets();
    }

    @Override
    @Nullable
    public CreationPreset worldpresets$selectedPreset()
    {
        return this.worldpresets$selected;
    }

    @Override
    public double worldpresets$listScroll()
    {
        return this.worldpresets$listScroll;
    }

    @Override
    public void worldpresets$setListScroll(double scroll)
    {
        this.worldpresets$listScroll = scroll;
    }

    @Override
    public void worldpresets$requestFocusRestore()
    {
        this.worldpresets$restoreFocus = true;
    }

    @Override
    public boolean worldpresets$consumeFocusRestore()
    {
        boolean restore = this.worldpresets$restoreFocus;
        this.worldpresets$restoreFocus = false;
        return restore;
    }

    @Unique
    private void worldpresets$applyWorldType(ResourceLocation worldType)
    {
        Registry<WorldPreset> registry = this.uiState.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET);
        var holder = registry.getHolder(ResourceKey.create(Registries.WORLD_PRESET, worldType));
        if (holder.isEmpty())
        {
            WorldPresets.LOGGER.warn("Preset world type {} is not a known world preset", worldType);
            return;
        }
        this.uiState.setWorldType(new WorldCreationUiState.WorldTypeEntry(holder.get()));
    }

    /** Vanilla's default game rules with the preset's overrides applied. */
    @Unique
    private static GameRules worldpresets$buildGameRules(CreationPreset preset)
    {
        Set<String> known = new HashSet<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor()
        {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type)
            {
                known.add(key.getId());
            }
        });
        CompoundTag tag = new CompoundTag();
        for (var entry : preset.gameRules().entrySet())
        {
            if (known.contains(entry.getKey())) tag.putString(entry.getKey(), entry.getValue());
            else WorldPresets.LOGGER.info("Ignoring unknown gamerule '{}' in preset {}", entry.getKey(), preset.id());
        }
        return new GameRules(new Dynamic<>(NbtOps.INSTANCE, tag));
    }
}