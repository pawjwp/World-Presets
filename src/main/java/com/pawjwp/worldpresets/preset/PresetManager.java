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
            if (!Files.isDirectory(dir))
            {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("example.json5"), EXAMPLE);
            }
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

    // Gson parsing to allow comments and unquoted keys
    private static final String EXAMPLE = """
            // Example world creation preset. Will not be shown in game if "hidden" is set to true.
            //
            // A picture for the preset can be placed in this folder with a matching name.
            // Images will be cropped to a 3:2 aspect ratio, the recommended image size is 768x512.
            //
            // All settings are optional.
            {
                hidden: true,
                title: "Example Preset",
                description: "A brief description, up to 5 lines long.\\nUse \\\\n for a new line.\\nSupports \\u00A7f\\u00A7oformatting codes\\u00A7r. Find more information at minecraft.wiki/w/Formatting_codes",
                // Set these to true to treat the title and/or description as translation keys instead of exact text.
                translate_title: false,
                translate_description: false,
                // Lower numbers sort first in the preset list.
                order: 0,

                // Fills options on the World and Game tabs. The player can still edit them.
                world: {
                    name: "Example Preset World",
                    gamemode: "survival",           // survival | hardcore | creative
                    difficulty: "normal",           // peaceful | easy | normal | hard
                    allow_cheats: false,
                    world_type: "minecraft:normal", // any world preset id like minecraft:flat or minecraft:large_biomes
                    seed: "01189998819991197253"    // takes any text or number, max of 32 characters
                },

                // Overrides default values in the Game Rules screen.
                gamerules: {
                    keepInventory: true,
                    mobGriefing: false
                },

                // Overrides where players spawn and respawn.
                spawn: {
                    dimension: "minecraft:overworld",
                    // How respawning works when the player has no usable bed or respawn anchor:
                    // vanilla: respawns go to the overworld
                    // last_dimension: respawns go to the last dimension where the spawnpoint was set (default)
                    // last_dimension_no_anchors: respawns go to the last dimension where the spawnpoint was set, excluding respawn anchors
                    // always_spawn_dimension: respawns go to the spawn dimension
                    respawn_mode: "last_dimension",
                    // Which spawn chunks stay loaded
                    // spawn_dimension: only the spawn dimension's, matching how vanilla keeps the overworld's (default)
                    // both: the spawn dimension and the overworld's chunks
                    keep_loaded: "spawn_dimension"
                },

                // Structures to generate upon creating the world.
                // Goes through the whole vanilla structure generation procedure including terrain adaptation and jigsaw placement.
                // An offset can be set [x, z] blocks away from the world spawn point.
                structures: [
                    { structure: "minecraft:village_plains", offset: [-64, -176]  },
                    { structure: "minecraft:pillager_outpost", offset: [-128, -64] }
                ]
            }
            """;
}