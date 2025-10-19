package com.lovetropics.perms.config;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.lovetropics.perms.override.RoleOverrideMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RoleConfigMap implements Iterable<Pair<String, RoleConfig>> {
    private static final Codec<String> ROLE_ID_CODEC = Codec.STRING.xmap(s -> s.toLowerCase(Locale.ROOT), s1 -> s1.toLowerCase(Locale.ROOT));
    private static final Codec<Map<String, RoleConfig>> ROLE_MAP_CODEC = Codec.unboundedMap(ROLE_ID_CODEC, RoleConfig.CODEC);

    private final Map<String, RoleConfig> roles;
    private final List<String> roleOrder;

    RoleConfigMap(Map<String, RoleConfig> roles, List<String> roleOrder) {
        this.roles = roles;
        this.roleOrder = roleOrder;
    }

    public static <T> RoleConfigMap parse(Dynamic<T> root, ConfigErrorConsumer error) {
        Map<String, RoleConfig> roles = ROLE_MAP_CODEC.parse(root)
                .resultOrPartial(err -> error.report("Failed to parse role config: " + err))
                .orElse(Map.of());
        Builder builder = new Builder();
        roles.forEach(builder::add);
        return builder.build(error);
    }

    @Nullable
    public RoleConfig get(String name) {
        return this.roles.get(name);
    }

    @Nonnull
    @Override
    public Iterator<Pair<String, RoleConfig>> iterator() {
        return Iterators.transform(this.roleOrder.iterator(), name -> Pair.of(name, this.roles.get(name)));
    }

    private static final class Builder {
        private final Map<String, RoleConfig> roles = new Object2ObjectOpenHashMap<>();
        private final List<String> roleOrder = new ArrayList<>();

        private Builder() {
        }

        public Builder add(String name, RoleConfig role) {
            this.roles.put(name, role);
            this.roleOrder.add(name);
            return this;
        }

        public RoleConfigMap build(ConfigErrorConsumer error) {
            Map<String, RoleConfig> roles = this.roles;
            List<String> order = this.sortRoles();

            roles = this.resolveIncludes(roles, order, error);

            return new RoleConfigMap(roles, order);
        }

        private Map<String, RoleConfig> resolveIncludes(Map<String, RoleConfig> roles, List<String> order, ConfigErrorConsumer error) {
            Map<String, RoleConfig> result = new Object2ObjectOpenHashMap<>(roles.size());

            for (String name : order) {
                RoleConfig role = roles.get(name);

                RoleOverrideMap.Builder resolvedOverrides = RoleOverrideMap.builder()
                        .addAll(role.overrides());

                // add includes to our resolved overrides with lower priority than our own
                for (String include : role.includes()) {
                    RoleConfig includeRole = result.get(include);
                    if (includeRole != null) {
                        resolvedOverrides.addAll(includeRole.overrides());
                    } else {
                        if (roles.containsKey(include)) {
                            error.report("'" + name + "' tried to include '" + include + "' but it is of a higher level");
                        } else {
                            error.report("'" + name + "' tried to include '" + include + "' but it does not exist");
                        }
                    }
                }

                result.put(name, new RoleConfig(resolvedOverrides.build(), List.of()));
            }

            return result;
        }

        private List<String> sortRoles() {
            return Lists.reverse(roleOrder);
        }
    }
}
