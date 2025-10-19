package com.lovetropics.perms.override;

import com.google.common.collect.Sets;
import com.lovetropics.lib.permission.role.RoleOverrideReader;
import com.lovetropics.lib.permission.role.RoleOverrideType;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RoleOverrideMap implements RoleOverrideReader {
    public static final RoleOverrideMap EMPTY = new RoleOverrideMap(Map.of());

    @SuppressWarnings("unchecked")
    public static final Codec<RoleOverrideMap> CODEC = Codec.dispatchedMap(RoleOverrideType.REGISTRY, t -> ExtraCodecs.compactListCodec((Codec<Object>) t.getCodec()))
            .xmap(RoleOverrideMap::new, m -> m.overrides);

    private final Map<RoleOverrideType<?>, List<Object>> overrides;
    private final Map<RoleOverrideType<?>, Object> combinedOverrides = new Reference2ObjectOpenHashMap<>();

    private RoleOverrideMap(Map<RoleOverrideType<?>, List<Object>> overrides) {
        this.overrides = new Reference2ObjectOpenHashMap<>(overrides);
        overrides.forEach((type, values) -> {
            combinedOverrides.put(type, combineOverridesUnchecked(type, values));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T combineOverridesUnchecked(RoleOverrideType<T> type, List<?> overrides) {
        return type.build((List<T>) overrides);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void notifyInitialize(ServerPlayer player) {
        for (RoleOverrideType<?> override : combinedOverrides.keySet()) {
            override.notifyInitialize(player);
        }
    }

    public void notifyChange(ServerPlayer player, Set<RoleOverrideType<?>> oldOverrides) {
        for (RoleOverrideType<?> override : Sets.union(combinedOverrides.keySet(), oldOverrides)) {
            override.notifyChange(player);
        }
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getOrNull(RoleOverrideType<T> type) {
        return (T) combinedOverrides.get(type);
    }

    @Override
    public Set<RoleOverrideType<?>> typeSet() {
        return combinedOverrides.keySet();
    }

    @Override
    public String toString() {
        return overrides.toString();
    }

    public static class Builder {
        private final Map<RoleOverrideType<?>, List<Object>> overrides = new Reference2ObjectOpenHashMap<>();

        private Builder() {
        }

        public Builder addAll(RoleOverrideReader overrides) {
            for (RoleOverrideType<?> type : overrides.typeSet()) {
                Object override = overrides.getOrNull(type);
                if (override != null) {
                    addUnchecked(type, override);
                }
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        private <T> Builder addUnchecked(RoleOverrideType<T> type, Object override) {
            getOrCreateOverrides(type).add((T) override);
            return this;
        }

        public <T> Builder addAll(RoleOverrideType<T> type, Collection<T> overrides) {
            getOrCreateOverrides(type).addAll(overrides);
            return this;
        }

        public <T> Builder add(RoleOverrideType<T> type, T override) {
            getOrCreateOverrides(type).add(override);
            return this;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> getOrCreateOverrides(RoleOverrideType<T> type) {
            return (List<T>) overrides.computeIfAbsent(type, t -> new ArrayList<>());
        }

        public RoleOverrideMap build() {
            return new RoleOverrideMap(overrides);
        }
    }
}
