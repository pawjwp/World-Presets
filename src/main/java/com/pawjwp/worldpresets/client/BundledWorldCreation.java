package com.pawjwp.worldpresets.client;

import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.CreationPreset;
import com.pawjwp.worldpresets.preset.CreationPreset.BundledWorld;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;


/** Creates a world from a preset's bundled world */
public final class BundledWorldCreation
{
    private BundledWorldCreation() {}

    /** Runs world creation with a bundled world */
    public static void create(
        Screen createScreen,
        WorldCreationUiState uiState,
        CreationPreset preset,
        @Nullable Path tempDataPackDir,
        Runnable removeTempPacks,
        @Nullable SelectedGameMode
        prefillGameMode
    ) {
        Minecraft minecraftInstance = Minecraft.getInstance();
        BundledWorld bundledWorld = preset.bundledWorld();
        String folderName = uiState.getTargetFolder();
        String worldName = uiState.getName();
        SelectedGameMode gamemode = uiState.getGameMode();
        boolean patchGamemode = prefillGameMode == null || gamemode != prefillGameMode;
        Difficulty difficulty = uiState.getDifficulty();
        boolean allowCheats = uiState.isAllowCheats();
        CompoundTag gameRules = uiState.getGameRules().createTag();
        WorldDataConfiguration dataConfig = uiState.getSettings().dataConfiguration();

        // Show a "preparing preset world" screen while loading
        minecraftInstance.forceSetScreen(new GenericDirtMessageScreen(Component.translatable("worldpresets.bundled_world.preparing")));
        Util.ioPool().execute(() ->
        {
            LevelStorageSource.LevelStorageAccess folderAccess = null;
            try
            {
                folderAccess = minecraftInstance.getLevelSource().createAccess(folderName);
                Path rootPath = folderAccess.getLevelPath(LevelResource.ROOT);

                // copy bundled world files, ignoring skipped files
                copyTree(bundledWorld.worldDir(), rootPath, relative -> skipped(bundledWorld, relative));
                // copy data packs
                if (tempDataPackDir != null) copyTree(tempDataPackDir, rootPath.resolve("datapacks"), relative -> false);
                // modify level.dat with selected options
                patchLevelDat(rootPath.resolve("level.dat").toFile(), bundledWorld, worldName, patchGamemode ? gamemode : null, difficulty, allowCheats, gameRules, dataConfig);

                folderAccess.close();
                minecraftInstance.execute(() ->
                {
                    removeTempPacks.run();
                    minecraftInstance.createWorldOpenFlows().loadLevel(null, folderName);
                });
            }
            catch (Exception e)
            {
                WorldPresets.LOGGER.error("Failed to create bundled world {}", bundledWorld.worldDir(), e);
                // Delete any world files so broken worlds aren't left in the saves folder
                if (folderAccess != null)
                {
                    try
                    {
                        folderAccess.deleteLevel();
                    }
                    catch (Exception cleanup)
                    {
                        WorldPresets.LOGGER.error("Failed to remove broken world {}", folderName, cleanup);
                    }
                }
                minecraftInstance.execute(() ->
                {
                    SystemToast.onWorldAccessFailure(minecraftInstance, folderName);
                    minecraftInstance.setScreen(createScreen);
                });
            }
        });
    }

    /** Copies every file under source into target, keeping the folder structure and overwriting existing files. */
    private static void copyTree(Path source, Path target, Predicate<Path> skipped) throws IOException
    {
        Files.walkFileTree(source, new SimpleFileVisitor<>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException
            {
                Path relative = source.relativize(dir);
                // return if directory is empty or on the skip list
                if (!relative.toString().isEmpty() && skipped.test(relative)) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
            {
                Path relativePath = source.relativize(file);
                // copy if file is not on the skipped list
                if (!skipped.test(relativePath)) Files.copy(file, target.resolve(relativePath), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Whether a given file should be copied or skipped */
    private static boolean skipped(BundledWorld bundledWorld, Path relativePath)
    {
        String fileName = relativePath.getFileName().toString();
        String folderName = relativePath.getName(0).toString();
        if (
            // Misc
            fileName.equals("session.lock") ||
            fileName.equals("level.dat_old") ||

            // All player data folders
            bundledWorld.resetPlayerData() && (
                folderName.equals("playerdata") ||
                folderName.equals("advancements") ||
                folderName.equals("stats") ||
                folderName.equals("players")
            ) ||
            
            // World state folders
            bundledWorld.resetWorldState() && (
                fileName.equals("raids.dat") ||
                fileName.equals("raids_end.dat")
            )
        ) return true;
        return false;
    }

    /**
     * Writes the selections from the world creation screen into level.dat
     */
    private static void patchLevelDat(File file,
        BundledWorld bundle,
        String name,
        @Nullable SelectedGameMode gameMode,
        Difficulty difficulty,
        boolean allowCheats,
        CompoundTag gameRules,
        WorldDataConfiguration dataConfiguration
    ) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file);
        CompoundTag data = root.getCompound("Data");
        root.put("Data", data);

        data.putString("LevelName", name);
        if (gameMode != null)
        {
            data.putInt("GameType", gameMode.gameType.getId());
            data.putBoolean("hardcore", gameMode == SelectedGameMode.HARDCORE);
        }
        data.putByte("Difficulty", (byte) difficulty.getId());
        data.putBoolean("allowCommands", allowCheats);
        data.put("GameRules", gameRules);
        data.putBoolean("confirmedExperimentalSettings", true);
        mergeDataPacks(data, dataConfiguration);

        if (bundledWorld.resetPlayerData())
        {
            data.remove("Player");
        }
        if (bundledWorld.resetWorldState())
        {
            data.putLong("DayTime", 0L);
            for (String key : List.of("raining", "rainTime", "thundering", "thunderTime", "clearWeatherTime",
                    "WanderingTraderId", "WanderingTraderSpawnDelay", "WanderingTraderSpawnChance",
                    "LastPlayed", "ServerBrands", "WasModded"))
            {
                data.remove(key);
            }
        }
        NbtIo.writeCompressed(root, file);
    }

    /** Merges existing data packs with any selected in the world creation screen */
    private static void mergeDataPacks(CompoundTag data, WorldDataConfiguration dataConfiguration)
    {
        CompoundTag dataPacks = data.getCompound("DataPacks");
        data.put("DataPacks", dataPacks);
        Set<String> enabled = stringSet(dataPacks.getList("Enabled", Tag.TAG_STRING));
        enabled.addAll(dataConfiguration.dataPacks().getEnabled());
        Set<String> disabled = stringSet(dataPacks.getList("Disabled", Tag.TAG_STRING));
        disabled.addAll(dataConfiguration.dataPacks().getDisabled());
        disabled.removeAll(enabled);
        dataPacks.put("Enabled", stringList(enabled));
        dataPacks.put("Disabled", stringList(disabled));

        Set<String> features = stringSet(data.getList("enabled_features", Tag.TAG_STRING));
        FeatureFlags.REGISTRY.toNames(dataConfiguration.enabledFeatures()).forEach(flag -> features.add(flag.toString()));
        data.put("enabled_features", stringList(features));
    }

    private static Set<String> stringSet(ListTag list)
    {
        Set<String> strings = new LinkedHashSet<>();
        for (Tag tag : list) strings.add(tag.getAsString());
        return strings;
    }

    private static ListTag stringList(Set<String> strings)
    {
        ListTag list = new ListTag();
        for (String string : strings) list.add(StringTag.valueOf(string));
        return list;
    }
}
