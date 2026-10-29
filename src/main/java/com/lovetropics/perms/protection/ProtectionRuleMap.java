package com.lovetropics.perms.protection;

import com.lovetropics.lib.permission.PermissionResult;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class ProtectionRuleMap {
    public static final ProtectionRuleMap EMPTY = new ProtectionRuleMap();

    public static final Codec<ProtectionRuleMap> CODEC = Codec.unboundedMap(ProtectionRule.CODEC, PermissionResult.CODEC).xmap(
            map -> {
                ProtectionRuleMap rules = new ProtectionRuleMap();
                rules.map.putAll(map);
                return rules;
            },
            rules -> rules.map
    );

    private final Map<ProtectionRule, PermissionResult> map;

    private ProtectionRuleMap() {
        this.map = new Reference2ObjectOpenHashMap<>();
    }

    ProtectionRuleMap(ProtectionRuleMap map) {
        this.map = new Reference2ObjectOpenHashMap<>(map.map);
    }

    private void set(ProtectionRule rule, PermissionResult result) {
        if (result != PermissionResult.PASS) {
            this.map.put(rule, result);
        } else {
            this.map.remove(rule);
        }
    }

    public ProtectionRuleMap withAll(ProtectionRuleMap other) {
        if (other.isEmpty()) {
            return this;
        }
        ProtectionRuleMap map = copy();
        map.map.putAll(other.map);
        return map;
    }

    public ProtectionRuleMap with(ProtectionRule rule, PermissionResult result) {
        if (this.test(rule) == result) {
            return this;
        }

        ProtectionRuleMap map = this.copy();
        map.set(rule, result);
        return map;
    }

    @Nonnull
    public PermissionResult test(ProtectionRule rule) {
        return this.map.getOrDefault(rule, PermissionResult.PASS);
    }

    public ProtectionRuleMap copy() {
        return new ProtectionRuleMap(this);
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public Set<ProtectionRule> keySet() {
        return Collections.unmodifiableSet(this.map.keySet());
    }
}
