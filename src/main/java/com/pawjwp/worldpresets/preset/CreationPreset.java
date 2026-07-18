package com.pawjwp.worldpresets.preset;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Difficulty;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
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
        @Nullable ResourceLocation spawnDimension
) {

    public enum GameMode
    {
        SURVIVAL,
        HARDCORE,
        CREATIVE
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
        if (json.has("spawn"))
        {
            JsonObject spawn = GsonHelper.getAsJsonObject(json, "spawn");
            if (spawn.has("dimension")) spawnDimension = ResourceLocation.parse(GsonHelper.getAsString(spawn, "dimension"));
        }

        return new CreationPreset(
                id, title, description, hidden, order,               // Meta information
                worldName, gameMode, difficulty, allowCheats,        // Game tab
                worldType, seed,                                     // World tab (not including generate structures/bonus chest toggles)
                Map.copyOf(gameRules),                               // More tab (not including data packs/experiments)
                spawnDimension                                       // Special settings
        );
    }

    private static Component text(JsonObject json, String key, String translateKey, String fallback)
    {
        String value = GsonHelper.getAsString(json, key, fallback);
        return GsonHelper.getAsBoolean(json, translateKey, false) ? Component.translatable(value) : Component.literal(value);
    }
}
