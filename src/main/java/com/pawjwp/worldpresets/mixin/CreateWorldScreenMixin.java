package com.pawjwp.worldpresets.mixin;

import com.mojang.serialization.Dynamic;
import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.client.BundledWorldCreation;
import com.pawjwp.worldpresets.client.PresetScreenAccess;
import com.pawjwp.worldpresets.client.PresetsTab;
import com.pawjwp.worldpresets.preset.CreationPreset;
import com.pawjwp.worldpresets.preset.PresetManager;
import com.pawjwp.worldpresets.world.PendingWorldSetup;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen implements PresetScreenAccess
{
    @Shadow
    @Final
    WorldCreationUiState uiState;

    @Shadow
    @Nullable
    private Path tempDataPackDir;

    @Shadow
    protected abstract void removeTempDataPackDir();

    @Unique
    @Nullable
    private CreationPreset worldpresets$selected;

    /** The world tab, used to disable worldgen controls for bundled worlds. */
    @Unique
    @Nullable
    private Tab worldpresets$worldTab;

    @Unique
    @Nullable
    private SelectedGameMode worldpresets$prefillGameMode;

    @Unique
    private double worldpresets$listScroll;

    @Unique
    private boolean worldpresets$restore;

    protected CreateWorldScreenMixin(Component title)
    {
        super(title);
    }

    /** Sends the selected preset to the server that is about to start when the Create button is pressed.
     * Alternatively, world creation is replaced by copying a bundled world. */
    @Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
    private void worldpresets$captureSetup(CallbackInfo ci)
    {
        CreationPreset preset = this.worldpresets$selected;
        if (preset != null && preset.bundledWorld() != null)
        {
            BundledWorldCreation.create(this, this.uiState, preset, this.tempDataPackDir, this::removeTempDataPackDir, this.worldpresets$prefillGameMode);
            ci.cancel();
            return;
        }
        PendingWorldSetup.set(preset);
    }

    /** Adds the Presets tab to the start of the Create World screen (when a valid presets is registered). */
    @ModifyArg(method = "init",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"))
    private Tab[] worldpresets$addPresetsTab(Tab[] tabs)
    {
        // Stores the second tab (world tab) so worldgen controls can be disabled
        this.worldpresets$worldTab = tabs.length > 1 ? tabs[1] : null;
        if (PresetManager.getPresets().isEmpty()) return tabs;
        Tab[] withPresets = new Tab[tabs.length + 1];
        withPresets[0] = new PresetsTab((CreateWorldScreen) (Object) this);
        System.arraycopy(tabs, 0, withPresets, 1, tabs.length);
        return withPresets;
    }

    /** Disables all controls on the World tab when using a preset with a bundled world. */
    @Inject(method = "init", at = @At("TAIL"))
    private void worldpresets$lockAfterInit(CallbackInfo ci)
    {
        // Locks worldgen controls after tabs are built
        this.uiState.addListener(state -> this.worldpresets$lockWorldgenControls());
        this.worldpresets$lockWorldgenControls();
    }

    @Unique
    private void worldpresets$lockWorldgenControls()
    {
        // If the preset includes a bundled world, lock all fields
        if (this.worldpresets$worldTab == null || this.worldpresets$selected == null || this.worldpresets$selected.bundledWorld() == null) return;
        this.worldpresets$worldTab.visitChildren(widget ->
        {
            widget.active = false;
            if (widget instanceof EditBox box) box.setEditable(false);
        });
    }

    @Override
    public void worldpresets$applyPreset(CreationPreset preset)
    {
        this.worldpresets$selected = preset;
        this.worldpresets$prefillGameMode = null;
        CompoundTag baseGamerules = new CompoundTag();
        if (preset.bundledWorld() != null)
        {
            this.worldpresets$prefillFromBundledWorld(preset.bundledWorld().prefill());
            baseGamerules = preset.bundledWorld().prefill().gameRules().copy();
        }
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
        if (!preset.gameRules().isEmpty() || !baseGamerules.isEmpty()) this.uiState.setGameRules(worldpresets$buildGameRules(preset, baseGamerules));
        // Gamemodes that can't be displayed will be applied as long as the selection is not changed in the UI
        if (preset.bundledWorld() != null && preset.gameMode() == null) this.worldpresets$prefillGameMode = this.uiState.getGameMode();
        // Rebuilds all widgets
        this.rebuildWidgets();
    }

    /** Loads the bundled world's level.dat settings into the create world screen. */
    @Unique
    private void worldpresets$prefillFromBundledWorld(CreationPreset.BundledWorld.Prefill prefill)
    {
        if (prefill.name() != null) this.uiState.setName(prefill.name());
        if (prefill.gameType() != null)
        {
            // Defaults to survival when the bundled world uses adventure or spectator mode, which are not options.
            // Unless the gamemode is manually changed in the UI, the bundled world's gamemode is kept.
            this.uiState.setGameMode(switch (GameType.byId(prefill.gameType()))
            {
                case CREATIVE -> SelectedGameMode.CREATIVE;
                case SURVIVAL -> prefill.hardcore() ? SelectedGameMode.HARDCORE : SelectedGameMode.SURVIVAL;
                default -> SelectedGameMode.SURVIVAL;
            });
        }
        if (prefill.difficulty() != null) this.uiState.setDifficulty(prefill.difficulty());
        if (prefill.allowCommands() != null) this.uiState.setAllowCheats(prefill.allowCommands());
        if (prefill.seed() != null) this.uiState.setSeed(Long.toString(prefill.seed()));
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
    public void worldpresets$requestRestore()
    {
        this.worldpresets$restore = true;
    }

    @Override
    public boolean worldpresets$consumeRestore()
    {
        boolean restore = this.worldpresets$restore;
        this.worldpresets$restore = false;
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

    /** Collection of vanilla gamerules */
    @Unique
    private static final Set<String> worldpresets$KNOWN_RULES = worldpresets$collectKnownRules();

    @Unique
    private static Set<String> worldpresets$collectKnownRules()
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
        return known;
    }

    /** Vanilla default game rules with overrides applied from the preset or bundled world. */
    @Unique
    private static GameRules worldpresets$buildGameRules(CreationPreset preset, CompoundTag tag)
    {
        for (var entry : preset.gameRules().entrySet())
        {
            if (worldpresets$KNOWN_RULES.contains(entry.getKey())) tag.putString(entry.getKey(), entry.getValue());
            else WorldPresets.LOGGER.info("Ignoring unknown gamerule '{}' in preset {}", entry.getKey(), preset.id());
        }
        return new GameRules(new Dynamic<>(NbtOps.INSTANCE, tag));
    }
}