package com.lovetropics.perms.config;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.lib.permission.role.RoleProvider;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.role.SimpleRole;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class RolesConfig implements RoleProvider {
    private static final RolesConfig DEFAULT_CONFIG = new RolesConfig(List.of(), SimpleRole.empty(Role.EVERYONE));

    private static RolesConfig instance = DEFAULT_CONFIG;

    private final Map<String, Role> roles;
    private final Role everyone;

    private RolesConfig(List<Role> roles, Role everyone) {
        ImmutableMap.Builder<String, Role> roleMap = ImmutableMap.builder();
        for (Role role : roles) {
            roleMap.put(role.id(), role);
        }
        this.roles = roleMap.build();

        this.everyone = everyone;
    }

    public static RolesConfig get() {
        return instance;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(LTPermissions.location("roles"), new SimplePreparableReloadListener<LoadResult>() {
            @Override
            protected LoadResult prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return load(resourceManager);
            }

            @Override
            protected void apply(LoadResult result, ResourceManager resourceManager, ProfilerFiller profiler) {
                RolesConfig.apply(result);
            }
        });
    }

    private static void apply(LoadResult result) {
        instance = result.config;
        PermissionsApi.setRoleProvider(result.config);
        LTPermissions.LOGGER.debug("Loaded {} roles", result.config.roles.size());
        result.config.roles.forEach((name, role) -> LTPermissions.LOGGER.debug("Role {} has configuration: {}", name, role));

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            PlayerRoleManager roleManager = PlayerRoleManager.get();
            roleManager.onRoleReload(server, RolesConfig.get());
        }
    }

    private static LoadResult load(ResourceManager resourceManager) {
        Optional<Resource> resource = resourceManager.getResource(LTPermissions.location("roles.json"));
        if (resource.isEmpty()) {
            return new LoadResult(DEFAULT_CONFIG, List.of("Found no roles config"));
        }

        List<String> errors = new ArrayList<>();
        ConfigErrorConsumer errorConsumer = error -> {
            LTPermissions.LOGGER.warn("{}", error);
            errors.add(error);
        };

        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonElement root = StrictJsonParser.parse(reader);
            RolesConfig config = parse(new Dynamic<>(JsonOps.INSTANCE, root), errorConsumer);
            return new LoadResult(config, errors);
        } catch (IOException e) {
            errorConsumer.report("Failed to read roles.json configuration", e);
        } catch (JsonSyntaxException e) {
            errorConsumer.report("Malformed syntax in roles.json configuration", e);
        }

        return new LoadResult(DEFAULT_CONFIG, errors);
    }

    public static List<String> reload(ResourceManager resourceManager) {
        LoadResult results = load(resourceManager);
        apply(results);
        return results.errors;
    }

    private static <T> RolesConfig parse(Dynamic<T> root, ConfigErrorConsumer error) {
        RoleConfigMap roleConfigs = RoleConfigMap.parse(root, error);

        Role everyone = SimpleRole.empty(Role.EVERYONE);
        List<Role> roles = new ArrayList<>();

        int index = 1;
        for (Pair<String, RoleConfig> entry : roleConfigs) {
            String name = entry.getFirst();
            RoleConfig roleConfig = entry.getSecond();

            if (!name.equalsIgnoreCase(Role.EVERYONE)) {
                roles.add(roleConfig.create(name, index++));
            } else {
                everyone = roleConfig.create(name, 0);
            }
        }

        return new RolesConfig(roles, everyone);
    }

    @Override
    @Nullable
    public Role get(String name) {
        if (name.equals(Role.EVERYONE)) {
            return this.everyone;
        }
        return this.roles.get(name);
    }

    @Nonnull
    public Role everyone() {
        return this.everyone;
    }

    @Nonnull
    @Override
    public Iterator<Role> iterator() {
        return Iterators.concat(this.roles.values().iterator(), Iterators.singletonIterator(this.everyone));
    }

    @Override
    public Stream<Role> stream() {
        return Stream.concat(this.roles.values().stream(), Stream.of(this.everyone));
    }

    private record LoadResult(RolesConfig config, List<String> errors) {
    }
}
