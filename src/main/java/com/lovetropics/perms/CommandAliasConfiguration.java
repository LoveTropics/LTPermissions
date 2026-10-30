package com.lovetropics.perms;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CommandAliasConfiguration(Map<String, Alias> aliases) {
    private static final CommandAliasConfiguration EMPTY = new CommandAliasConfiguration(Map.of());

    public static final Codec<CommandAliasConfiguration> CODEC = Codec.unboundedMap(Codec.STRING, Alias.CODEC)
            .xmap(CommandAliasConfiguration::new, CommandAliasConfiguration::aliases);

    private static final ThreadLocal<@Nullable ResourceManager> RESOURCE_MANAGER = new ThreadLocal<>();

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

    public record Alias(
            List<String> commands,
            boolean suppressOutput
    ) {
        private static final Codec<List<String>> COMMANDS_CODEC = ExtraCodecs.compactListCodec(Codec.STRING);

        public static final Codec<Alias> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
                COMMANDS_CODEC.fieldOf("commands").forGetter(Alias::commands),
                Codec.BOOL.optionalFieldOf("suppress_output", true).forGetter(Alias::suppressOutput)
        ).apply(i, Alias::new));

        public static final Codec<Alias> CODEC = Codec.either(COMMANDS_CODEC, FULL_CODEC).xmap(
                either -> either.map(commands -> new Alias(commands, true), alias -> alias),
                alias -> alias.suppressOutput ? Either.left(alias.commands) : Either.right(alias)
        );
    }
}
