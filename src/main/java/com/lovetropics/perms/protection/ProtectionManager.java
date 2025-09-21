package com.lovetropics.perms.protection;

import com.google.common.collect.Lists;
import com.lovetropics.lib.permission.PermissionResult;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.protection.authority.Authority;
import com.lovetropics.perms.protection.authority.BuiltinAuthority;
import com.lovetropics.perms.protection.authority.UserAuthority;
import com.lovetropics.perms.protection.authority.behavior.AuthorityBehaviorMap;
import com.lovetropics.perms.protection.authority.behavior.config.AuthorityBehaviorConfigs;
import com.lovetropics.perms.protection.authority.map.AuthorityMap;
import com.lovetropics.perms.protection.authority.map.IndexedAuthorityMap;
import com.lovetropics.perms.protection.authority.map.SortedAuthorityHashMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class ProtectionManager extends SavedData {
    private static final String KEY = "protection";
    private static final SavedDataType<ProtectionManager> TYPE = new SavedDataType<>(
            KEY,
            ProtectionManager::new,
            Packed.CODEC.xmap(ProtectionManager::new, ProtectionManager::asPacked)
    );

    private final SortedAuthorityHashMap<UserAuthority> userAuthorities = new SortedAuthorityHashMap<>();
    private final IndexedAuthorityMap<Authority> allAuthorities = new IndexedAuthorityMap<>();

    private BuiltinAuthority builtinUniverse = BuiltinAuthority.universe();
    private final Reference2ObjectMap<ResourceKey<Level>, BuiltinAuthority> builtinDimensions = new Reference2ObjectOpenHashMap<>();

    private ProtectionManager() {
        this.allAuthorities.add(this.builtinUniverse);
    }

    private ProtectionManager(Packed packed) {
        this();
        packed.authorities.forEach(this::addAuthority);
        packed.builtin.dimensions.forEach(this::addBuiltinDimension);
        addBuiltinUniverse(packed.builtin.universe);
        invalidateBehaviors();
    }

    private Packed asPacked() {
        return new Packed(
                Lists.newArrayList(userAuthorities),
                new Packed.Builtin(
                        builtinDimensions.entrySet().stream()
                                .filter(entry -> !entry.getValue().isEmpty())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                        builtinUniverse
                )
        );
    }

    public static ProtectionManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public PermissionResult test(EventSource source, ProtectionRule rule) {
        Iterable<Authority> authorities = this.allAuthorities.selectByDimension(source, rule);
        for (Authority authority : authorities) {
            if (!authority.eventFilter().accepts(source)) {
                continue;
            }

            PermissionResult result = authority.rules().test(rule);
            if (result.isTerminator()) {
                return result;
            }
        }

        return PermissionResult.PASS;
    }

    public boolean denies(EventSource source, ProtectionRule rule) {
        return this.test(source, rule).isDenied();
    }

    public boolean denies(EventSource source, ProtectionRule... rules) {
        for (final ProtectionRule rule : rules) {
            final PermissionResult result = test(source, rule);
            if (result.isTerminator()) {
                return result.isDenied();
            }
        }
        return false;
    }

    @Nullable
    public AuthorityMap<Authority> selectWithBehavior(ResourceKey<Level> dimension) {
        return this.allAuthorities.selectWithBehavior(dimension);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protection = get(level.getServer());
            protection.onLevelLoad(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protection = get(level.getServer());
            protection.onLevelUnload(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (AuthorityBehaviorConfigs.hasReloaded()) {
                ProtectionManager protection = get(level.getServer());
                protection.onReload();
            }
        }
    }

    private void onLevelLoad(ServerLevel level) {
        if (!this.builtinDimensions.containsKey(level.dimension())) {
            BuiltinAuthority dimension = BuiltinAuthority.dimension(level.dimension());
            this.addBuiltinDimension(level.dimension(), dimension);
        }

        this.allAuthorities.addDimensionIndex(level.dimension());
    }

    private void onLevelUnload(ServerLevel level) {
        BuiltinAuthority dimension = this.builtinDimensions.get(level.dimension());
        if (dimension != null && dimension.isEmpty()) {
            this.builtinDimensions.remove(level.dimension());
            this.allAuthorities.remove(dimension);
        }

        this.allAuthorities.removeDimensionIndex(level.dimension());
    }

    private void onReload() {
        List<Authority> authoritiesWithBehavior = new ArrayList<>();
        for (Authority authority : this.allAuthorities) {
            if (authority.hasBehavior()) {
                authoritiesWithBehavior.add(authority);
            }
        }

        for (Authority authority : authoritiesWithBehavior) {
            AuthorityBehaviorMap behavior = authority.behavior().rebuild();
            this.replaceAuthority(authority, authority.withBehavior(behavior));
        }
    }

    public boolean addAuthority(UserAuthority authority) {
        if (this.userAuthorities.add(authority)) {
            this.allAuthorities.add(authority);
            this.invalidateBehaviors();
            return true;
        } else {
            return false;
        }
    }

    public boolean removeAuthority(UserAuthority authority) {
        if (this.userAuthorities.remove(authority)) {
            this.allAuthorities.remove(authority);
            this.invalidateBehaviors();
            return true;
        } else {
            return false;
        }
    }

    public void replaceAuthority(Authority from, Authority to) {
        if (this.allAuthorities.replace(from, to)) {
            if (from instanceof UserAuthority && to instanceof UserAuthority) {
                this.replaceUserAuthority((UserAuthority) from, (UserAuthority) to);
            }

            if (from instanceof BuiltinAuthority && to instanceof BuiltinAuthority) {
                this.replaceBuiltinAuthority((BuiltinAuthority) from, (BuiltinAuthority) to);
            }

            this.invalidateBehaviors();
        }
    }

    private void replaceUserAuthority(UserAuthority from, UserAuthority to) {
        this.userAuthorities.replace(from, to);
    }

    private void replaceBuiltinAuthority(BuiltinAuthority from, BuiltinAuthority to) {
        if (from == this.builtinUniverse) {
            this.builtinUniverse = to;
            return;
        }

        for (Reference2ObjectMap.Entry<ResourceKey<Level>, BuiltinAuthority> entry : Reference2ObjectMaps.fastIterable(this.builtinDimensions)) {
            BuiltinAuthority authority = entry.getValue();
            if (authority == from) {
                entry.setValue(to);
                return;
            }
        }
    }

    private void invalidateBehaviors() {
        ProtectionPlayerTracker.INSTANCE.invalidate();
    }

    @Nullable
    public Authority getAuthorityByKey(String key) {
        return this.allAuthorities.byKey(key);
    }

    @Nullable
    public UserAuthority getUserAuthorityByKey(String key) {
        return this.userAuthorities.byKey(key);
    }

    public Stream<Authority> allAuthorities() {
        return this.allAuthorities.stream();
    }

    public Stream<UserAuthority> userAuthorities() {
        return this.userAuthorities.stream();
    }

    private record Packed(
            List<UserAuthority> authorities,
            Builtin builtin
    ) {
        public static final Codec<Packed> CODEC = RecordCodecBuilder.create(i -> i.group(
                UserAuthority.CODEC.listOf().fieldOf("authorities").forGetter(Packed::authorities),
                Builtin.CODEC.fieldOf("builtin").forGetter(Packed::builtin)
        ).apply(i, Packed::new));

        private record Builtin(
                Map<ResourceKey<Level>, BuiltinAuthority> dimensions,
                BuiltinAuthority universe
        ) {
            public static final Codec<Builtin> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.dispatchedMap(ResourceKey.codec(Registries.DIMENSION), BuiltinAuthority::dimensionCodec).optionalFieldOf("dimensions", Map.of()).forGetter(Builtin::dimensions),
                    BuiltinAuthority.universeCodec().fieldOf("universe").forGetter(Builtin::universe)
            ).apply(i, Builtin::new));
        }
    }

    private void addBuiltinDimension(ResourceKey<Level> dimension, BuiltinAuthority authority) {
        BuiltinAuthority lastAuthority = this.builtinDimensions.put(dimension, authority);
        if (lastAuthority == null) {
            this.allAuthorities.add(authority);
        } else {
            this.allAuthorities.replace(lastAuthority, authority);
        }
    }

    private void addBuiltinUniverse(BuiltinAuthority authority) {
        BuiltinAuthority lastAuthority = this.builtinUniverse;
        this.builtinUniverse = authority;
        this.allAuthorities.replace(lastAuthority, authority);
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
