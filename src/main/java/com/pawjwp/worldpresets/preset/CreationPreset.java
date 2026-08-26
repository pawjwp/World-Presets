package com.pawjwp.worldpresets.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pawjwp.worldpresets.WorldPresets;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A world creation preset defined by a pack author in config/worldpresets/presets/_____.json5.
 * Every field is optional, a preset only changes the defined options.
 */
public record CreationPreset(
        // Meta information
        String id,
        Component title,
        Component description,
        boolean hidden,
        int order,

        // Game tab
        @Nullable String worldName,
        @Nullable GameMode gameMode,
        @Nullable Difficulty difficulty,
        @Nullable Boolean allowCheats,

        // World tab (not including generate structures/bonus chest toggles)
        @Nullable ResourceLocation worldType,
        @Nullable String seed,

        // More tab (not including data packs/experiments)
        Map<String, String> gameRules,

        // Special settings
        @Nullable ResourceLocation spawnDimension,
        RespawnMode respawnMode,
        SpawnChunkLoading keepLoaded,
        @Nullable StartPosition startPosition,
        List<StructureSpec> structures,
        @Nullable BundledWorld bundledWorld
) {

    public enum GameMode
    {
        SURVIVAL,
        HARDCORE,
        CREATIVE
    }

    /** Respawn behavior when a preset has a custom spawn dimension and the player has no set spawn */
    public enum RespawnMode
    {
        // Respawns go to the overworld
        VANILLA,
        // Respawns go to the last dimension where the player's spawnpoint was set
        LAST_DIMENSION,
        // Respawns go to the last dimension where the player's spawnpoint was set, excluding respawn anchors
        LAST_DIMENSION_NO_ANCHORS,
        // Respawns go to the spawn dimension
        ALWAYS_SPAWN_DIMENSION;

        public static RespawnMode byName(String name)
        {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Which dimension's spawn chunks stay loaded when a preset has a custom spawn dimension */
    public enum SpawnChunkLoading
    {
        // Only the spawn dimension's spawn chunks stay loaded
        SPAWN_DIMENSION,
        // Both the spawn dimension's and the overworld's normal spawn chunks stay loaded
        BOTH;

        public static SpawnChunkLoading byName(String name)
        {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** How the set spawn position is converted to the world spawn */
    public enum Placement
    {
        // Place the player at the exact set coordinates, even if that is in a wall or mid-air
        EXACT,
        // Place the player at the exact coordinates, clearing a space and adding a floor if needed
        CLEAR,
        // Find the nearest safe surface at the coordinates' column (ignores spawn height)
        FIND_SAFE,
        // Relocate vanilla's entire spawn climate placement logic around the set coordinate (ignores spawn height)
        FIND_CLIMATE;

        public static Placement byName(String name)
        {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** World spawn override, built from command-style coordinates. Each axis is either absolute or an offset using a "~". */
    public record StartPosition(Coord x, Coord y, Coord z, Placement placement)
    {
        public record Coord(boolean relative, int value)
        {
            public int resolve(int base)
            {
                return relative ? base + value : value;
            }
        }

        public boolean anyRelative()
        {
            return x.relative() || y.relative() || z.relative();
        }

        /** Exact and clear place the player at the precise coordinates and skip the inprecision of vanilla's spawns. */
        public boolean exact()
        {
            return placement == Placement.EXACT || placement == Placement.CLEAR;
        }

        private static Coord parseCoord(String token)
        {
            if (token.startsWith("~"))
            {
                String offset = token.substring(1);
                return new Coord(true, offset.isEmpty() ? 0 : Integer.parseInt(offset));
            }
            return new Coord(false, Integer.parseInt(token));
        }

        public static StartPosition parse(JsonObject json)
        {
            String[] tokens = GsonHelper.getAsString(json, "position").trim().split("[\\s,]+");
            if (tokens.length != 3) throw new IllegalArgumentException("start_position must be three coordinates \"x y z\", got " + tokens.length);
            Placement placement = json.has("placement") ? Placement.byName(GsonHelper.getAsString(json, "placement")) : Placement.FIND_SAFE;
            return new StartPosition(parseCoord(tokens[0]), parseCoord(tokens[1]), parseCoord(tokens[2]), placement);
        }
    }

    /** A structure to generate near world spawn */
    public record StructureSpec(ResourceLocation structure, int offsetX, int offsetZ) {}

    /** A pre-made world that comes bundled with a preset saved to config/worldpresets/worlds */
    public record BundledWorld(Path worldDir, boolean resetPlayerData, boolean resetWorldState, Prefill prefill)
    {
        /** Values read from bundled level.dat to prefill the tabs in the world creation screen */
        public record Prefill(@Nullable String name, @Nullable Integer gameType, boolean hardcore, @Nullable Difficulty difficulty,
                              @Nullable Boolean allowCommands, CompoundTag gameRules, @Nullable Long seed) {}

        /** Parses and validates the bundled world, throwing errors if invalid */
        public static BundledWorld parse(JsonObject json)
        {
            String worldFolder = GsonHelper.getAsString(json, "folder");
            Path worldsDir = PresetManager.worldsDirectory().resolve(worldFolder);
            if (!Files.isDirectory(worldsDir)) throw new IllegalArgumentException("Bundled world folder not found: " + worldsDir);
            if (!Files.isRegularFile(worldsDir.resolve("level.dat"))) throw new IllegalArgumentException("Bundled world '" + worldFolder + "' has no level.dat");
            CompoundTag data;
            try
            {
                data = NbtIo.readCompressed(worldsDir.resolve("level.dat").toFile()).getCompound("Data");
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Bundled world '" + worldFolder + "' has an invalid level.dat", e);
            }
            // Reject worlds created on a newer version
            int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            if (data.getInt("DataVersion") > currentVersion)
                throw new IllegalArgumentException("Bundled world '" + worldFolder + "' was saved in a newer game version (world version is " + data.getInt("DataVersion") + ", this game is " + currentVersion + ")");

            Prefill prefill = new Prefill(
                    data.contains("LevelName", Tag.TAG_STRING) ? data.getString("LevelName") : null,
                    data.contains("GameType", Tag.TAG_ANY_NUMERIC) ? data.getInt("GameType") : null,
                    data.getBoolean("hardcore"),
                    data.contains("Difficulty", Tag.TAG_ANY_NUMERIC) ? Difficulty.byId(data.getInt("Difficulty")) : null,
                    data.contains("allowCommands", Tag.TAG_ANY_NUMERIC) ? data.getBoolean("allowCommands") : null,
                    data.getCompound("GameRules"),
                    data.getCompound("WorldGenSettings").contains("seed", Tag.TAG_ANY_NUMERIC) ? data.getCompound("WorldGenSettings").getLong("seed") : null);
            return new BundledWorld(worldsDir,
                    GsonHelper.getAsBoolean(json, "reset_player_data", true),
                    GsonHelper.getAsBoolean(json, "reset_world_state", false),
                    prefill);
        }
    }

    public static CreationPreset parse(String id, JsonObject json)
    {
        Component title = text(json, "title", "translate_title", id);
        Component description = text(json, "description", "translate_description", "");
        boolean hidden = GsonHelper.getAsBoolean(json, "hidden", false);
        int order = GsonHelper.getAsInt(json, "order", 0);

        String worldName = null;
        GameMode gameMode = null;
        Difficulty difficulty = null;
        Boolean allowCheats = null;
        ResourceLocation worldType = null;
        String seed = null;
        if (json.has("world"))
        {
            JsonObject world = GsonHelper.getAsJsonObject(json, "world");
            if (world.has("name")) worldName = GsonHelper.getAsString(world, "name");
            if (world.has("gamemode")) gameMode = GameMode.valueOf(GsonHelper.getAsString(world, "gamemode").toUpperCase(Locale.ROOT));
            if (world.has("difficulty"))
            {
                difficulty = Difficulty.byName(GsonHelper.getAsString(world, "difficulty"));
                if (difficulty == null) throw new IllegalArgumentException("Unknown difficulty: " + GsonHelper.getAsString(world, "difficulty"));
            }
            if (world.has("allow_cheats")) allowCheats = GsonHelper.getAsBoolean(world, "allow_cheats");
            if (world.has("world_type")) worldType = ResourceLocation.parse(GsonHelper.getAsString(world, "world_type"));
            if (world.has("seed")) seed = GsonHelper.getAsString(world, "seed");
        }

        Map<String, String> gameRules = new LinkedHashMap<>();
        if (json.has("gamerules"))
        {
            for (var entry : GsonHelper.getAsJsonObject(json, "gamerules").entrySet())
            {
                gameRules.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        ResourceLocation spawnDimension = null;
        // Default to vanilla spawning unless a spawn dimension is set
        RespawnMode respawnMode = RespawnMode.VANILLA;
        SpawnChunkLoading keepLoaded = SpawnChunkLoading.SPAWN_DIMENSION;
        if (json.has("dimension"))
        {
            JsonObject dimension = GsonHelper.getAsJsonObject(json, "dimension");
            // The dimension id defaults to the overworld so respawn_mode can be set on its own
            spawnDimension = dimension.has("dimension_id") ? ResourceLocation.parse(GsonHelper.getAsString(dimension, "dimension_id")) : Level.OVERWORLD.location();
            // If a spawn dimension is set, default to last_dimension respawn mode
            if (dimension.has("dimension_id")) respawnMode = RespawnMode.LAST_DIMENSION;
            if (dimension.has("respawn_mode")) respawnMode = RespawnMode.byName(GsonHelper.getAsString(dimension, "respawn_mode"));
            if (dimension.has("keep_loaded")) keepLoaded = SpawnChunkLoading.byName(GsonHelper.getAsString(dimension, "keep_loaded"));
        }

        StartPosition startPosition = json.has("start_position") ? StartPosition.parse(GsonHelper.getAsJsonObject(json, "start_position")) : null;

        List<StructureSpec> structures = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "structures", new JsonArray()))
        {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "structure entry");
            int offsetX = 0;
            int offsetZ = 0;
            if (entry.has("offset"))
            {
                JsonArray offset = GsonHelper.getAsJsonArray(entry, "offset");
                if (offset.size() != 2) throw new IllegalArgumentException("Structure offset must be two numbers [x, z], got " + offset);
                offsetX = offset.get(0).getAsInt();
                offsetZ = offset.get(1).getAsInt();
            }
            structures.add(new StructureSpec(ResourceLocation.parse(GsonHelper.getAsString(entry, "structure")), offsetX, offsetZ));
        }

        BundledWorld bundledWorld = json.has("bundled_world") ? BundledWorld.parse(GsonHelper.getAsJsonObject(json, "bundled_world")) : null;
        if (bundledWorld != null && (worldType != null || seed != null || spawnDimension != null || startPosition != null || !structures.isEmpty()))
        {
            WorldPresets.LOGGER.warn("Preset {} includes a bundled world; its world_type, seed, dimension, start_position, and structures settings are ignored", id);
            worldType = null;
            seed = null;
            spawnDimension = null;
            startPosition = null;
            structures = List.of();
        }

        return new CreationPreset(
                id, title, description, hidden, order,               // Meta information
                worldName, gameMode, difficulty, allowCheats,        // Game tab
                worldType, seed,                                     // World tab (not including generate structures/bonus chest toggles)
                Map.copyOf(gameRules),                               // More tab (not including data packs/experiments)
                spawnDimension, respawnMode, keepLoaded, startPosition, List.copyOf(structures), // Special settings
                bundledWorld
        );
    }

    private static Component text(JsonObject json, String key, String translateKey, String fallback)
    {
        String value = GsonHelper.getAsString(json, key, fallback);
        return GsonHelper.getAsBoolean(json, translateKey, false) ? Component.translatable(value) : Component.literal(value);
    }
}
