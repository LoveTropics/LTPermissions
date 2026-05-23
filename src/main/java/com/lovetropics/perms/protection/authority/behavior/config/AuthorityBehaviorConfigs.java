package com.lovetropics.perms.protection.authority.behavior.config;

import com.google.gson.JsonElement;
import com.lovetropics.lib.codec.CodecRegistry;
import com.lovetropics.perms.LTPermissions;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class AuthorityBehaviorConfigs {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final CodecRegistry<Identifier, AuthorityBehaviorConfig> REGISTRY = CodecRegistry.idKeys();

    private static final FileToIdConverter FILE_TO_ID_CONVERTER = FileToIdConverter.json("authority_behaviors");

    private static final AtomicBoolean RELOADED = new AtomicBoolean();

    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(LTPermissions.location("authority_behaviors"), (sharedState, executor, preparationBarrier, gameExecutor) ->
                CompletableFuture.supplyAsync(() -> load(sharedState.resourceManager()), executor)
                        .thenCompose(preparationBarrier::wait)
                        .thenAcceptAsync(configs -> {
                            REGISTRY.clear();
                            configs.forEach(REGISTRY::register);
                            RELOADED.set(true);
                        }, gameExecutor));
    }

    private static Map<Identifier, AuthorityBehaviorConfig> load(ResourceManager resourceManager) {
        Map<Identifier, AuthorityBehaviorConfig> result = new Object2ObjectOpenHashMap<>();

        Map<Identifier, Resource> resources = FILE_TO_ID_CONVERTER.listMatchingResources(resourceManager);
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier location = entry.getKey();
            try {
                Identifier id = FILE_TO_ID_CONVERTER.fileToId(location);
                loadConfig(entry.getValue())
                        .resultOrPartial(error -> LOGGER.error("Failed to load game authority behavior at {}: {}", location, error))
                        .ifPresent(config -> result.put(id, config));
            } catch (Exception e) {
                LOGGER.error("Failed to load authority behavior config at {}", location, e);
            }
        }

        return result;
    }

    private static DataResult<AuthorityBehaviorConfig> loadConfig(Resource resource) throws IOException {
        try (BufferedReader reader = resource.openAsReader()) {
            JsonElement json = StrictJsonParser.parse(reader);
            return AuthorityBehaviorConfig.CODEC.parse(JsonOps.INSTANCE, json);
        }
    }

    public static boolean hasReloaded() {
        return RELOADED.compareAndSet(true, false);
    }
}
