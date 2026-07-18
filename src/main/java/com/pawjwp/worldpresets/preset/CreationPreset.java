package com.pawjwp.worldpresets.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Difficulty;

import javax.annotation.Nullable;
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
        List<StructureSpec> structures
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

    /** A structure to generate near world spawn */
    public record StructureSpec(ResourceLocation structure, int offsetX, int offsetZ) {}

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
            if (world.has("seed")) seed = world.get("seed").getAsString();
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
        RespawnMode respawnMode = RespawnMode.LAST_DIMENSION;
        if (json.has("spawn"))
        {
            JsonObject spawn = GsonHelper.getAsJsonObject(json, "spawn");
            if (spawn.has("dimension")) spawnDimension = ResourceLocation.parse(GsonHelper.getAsString(spawn, "dimension"));
            if (spawn.has("respawn_mode")) respawnMode = RespawnMode.byName(GsonHelper.getAsString(spawn, "respawn_mode"));
        }

        List<StructureSpec> structures = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "structures", new JsonArray()))
        {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "structure entry");
            int offsetX = 0;
            int offsetZ = 0;
            if (entry.has("offset"))
            {
                JsonArray offset = GsonHelper.getAsJsonArray(entry, "offset");
                offsetX = offset.get(0).getAsInt();
                offsetZ = offset.get(1).getAsInt();
            }
            structures.add(new StructureSpec(ResourceLocation.parse(GsonHelper.getAsString(entry, "structure")), offsetX, offsetZ));
        }

        return new CreationPreset(
                id, title, description, hidden, order,               // Meta information
                worldName, gameMode, difficulty, allowCheats,        // Game tab
                worldType, seed,                                     // World tab (not including generate structures/bonus chest toggles)
                Map.copyOf(gameRules),                               // More tab (not including data packs/experiments)
                spawnDimension, respawnMode, List.copyOf(structures) // Special settings
        );
    }

    private static Component text(JsonObject json, String key, String translateKey, String fallback)
    {
        String value = GsonHelper.getAsString(json, key, fallback);
        return GsonHelper.getAsBoolean(json, translateKey, false) ? Component.translatable(value) : Component.literal(value);
    }
}
