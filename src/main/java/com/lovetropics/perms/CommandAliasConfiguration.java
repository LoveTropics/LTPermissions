package com.lovetropics.perms;

import com.lovetropics.lib.codec.MoreCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public record CommandAliasConfiguration(Map<String, String[]> aliases) {
    private static final CommandAliasConfiguration EMPTY = new CommandAliasConfiguration(Map.of());

    public static final Codec<CommandAliasConfiguration> CODEC = Codec.unboundedMap(Codec.STRING, MoreCodecs.arrayOrUnit(Codec.STRING, String[]::new))
            .xmap(CommandAliasConfiguration::new, CommandAliasConfiguration::aliases);

    private static final ThreadLocal<ResourceManager> RESOURCE_MANAGER = new ThreadLocal<>();

    public static void setResourceManager(ResourceManager resourceManager) {
        RESOURCE_MANAGER.set(resourceManager);
    }

    public static void clearResourceManager() {
        RESOURCE_MANAGER.remove();
    }

    public static CommandAliasConfiguration load() {
        ResourceManager resourceManager = RESOURCE_MANAGER.get();
        if (resourceManager == null) {
            LTPermissions.LOGGER.error("Resources not available, not loading command aliases");
            return CommandAliasConfiguration.EMPTY;
        }
        Optional<Resource> resource = resourceManager.getResource(LTPermissions.location("command_aliases.json"));
        if (resource.isEmpty()) {
            return CommandAliasConfiguration.EMPTY;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            return CODEC.parse(JsonOps.INSTANCE, StrictJsonParser.parse(reader))
                    .resultOrPartial(error -> LTPermissions.LOGGER.warn("Malformed command aliases configuration: {}", error))
                    .orElse(EMPTY);
        } catch (IOException e) {
            LTPermissions.LOGGER.warn("Failed to load command_aliases.json configuration", e);
            return EMPTY;
        }
    }
}
