package com.pawjwp.worldpresets.preset;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.pawjwp.worldpresets.WorldPresets;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads presets from config/worldpresets/presets
 */
public final class PresetManager
{
    private static List<CreationPreset> presets = List.of();

    public static Path directory()
    {
        return FMLPaths.CONFIGDIR.get().resolve(WorldPresets.MODID).resolve("presets");
    }

    //Visible presets, sorted
    public static List<CreationPreset> getPresets()
    {
        return presets;
    }

    public static void reload()
    {
        Path dir = directory();
        List<CreationPreset> loadedPresets = new ArrayList<>();
        try
        {
            try (Stream<Path> files = Files.list(dir))
            {
                for (Path file : files.toList())
                {
                    String fileName = file.getFileName().toString();
                    if (!fileName.endsWith(".json5") && !fileName.endsWith(".json")) continue;
                    String id = fileName.substring(0, fileName.lastIndexOf('.'));
                    try (JsonReader reader = new JsonReader(Files.newBufferedReader(file)))
                    {
                        reader.setLenient(true);
                        CreationPreset preset = CreationPreset.parse(id, JsonParser.parseReader(reader).getAsJsonObject());
                        if (!preset.hidden()) loadedPresets.add(preset);
                    }
                    catch (Exception e)
                    {
                        WorldPresets.LOGGER.error("Failed to load world creation preset {}", fileName, e);
                    }
                }
            }
        }
        catch (IOException e)
        {
            WorldPresets.LOGGER.error("Failed to read world creation presets directory", e);
        }
        loadedPresets.sort(Comparator.comparingInt(CreationPreset::order).thenComparing(CreationPreset::id));
        presets = loadedPresets;
    }
}