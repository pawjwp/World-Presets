package com.pawjwp.worldpresets.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.pawjwp.worldpresets.WorldPresets;
import com.pawjwp.worldpresets.preset.CreationPreset;
import com.pawjwp.worldpresets.preset.PresetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Loads a preset's image at config/worldpresets/presets/_____.png
// Falls back to a .jpg or .jpeg if no .png is present. If no images are present, a placeholder image is used.
public final class PresetImages
{
    public record Image(ResourceLocation texture, int width, int height) {}

    private static final Image PLACEHOLDER = new Image(ResourceLocation.fromNamespaceAndPath(WorldPresets.MODID, "textures/gui/preset_placeholder.png"), 768, 512);
    private static final Map<String, Image> CACHE = new HashMap<>();
    private static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg");

    public static Image get(CreationPreset preset)
    {
        return CACHE.computeIfAbsent(preset.id(), PresetImages::load);
    }

    public static void reload()
    {
        CACHE.values().forEach(image -> {
            if (image != PLACEHOLDER) Minecraft.getInstance().getTextureManager().release(image.texture());
        });
        CACHE.clear();
    }

    private static Image load(String id)
    {
        // Uses a PNG if available, a JPG/JPEG if not, and the placeholder image if no specific image is present
        Path file = EXTENSIONS.stream()
                .map(extension -> PresetManager.directory().resolve(id + "." + extension))
                .filter(Files::isRegularFile)
                .findFirst().orElse(null);
        if (file == null) return PLACEHOLDER;
        try (InputStream in = Files.newInputStream(file))
        {
            NativeImage image = NativeImage.read(in);
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(WorldPresets.MODID,
                    "preset/" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"));
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(image));
            return new Image(texture, image.getWidth(), image.getHeight());
        }
        catch (IOException e)
        {
            WorldPresets.LOGGER.error("Failed to load preset image {}", file, e);
            return PLACEHOLDER;
        }
    }
}
