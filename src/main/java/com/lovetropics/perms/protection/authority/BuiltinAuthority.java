package com.lovetropics.perms.protection.authority;

import com.lovetropics.lib.permission.PermissionResult;
import com.lovetropics.perms.protection.EventFilter;
import com.lovetropics.perms.protection.ProtectionExclusions;
import com.lovetropics.perms.protection.ProtectionRule;
import com.lovetropics.perms.protection.ProtectionRuleMap;
import com.lovetropics.perms.protection.authority.behavior.AuthorityBehaviorMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class BuiltinAuthority implements Authority {
    public static final int UNIVERSE_LEVEL = Integer.MIN_VALUE;
    public static final int DIMENSION_LEVEL = Integer.MIN_VALUE + 1;

    private final String key;
    private final int level;
    private final EventFilter filter;
    private final ProtectionRuleMap rules;
    private final ProtectionExclusions exclusions;
    private final AuthorityBehaviorMap behavior;

    private final EventFilter filterWithExclusions;

    private BuiltinAuthority(String key, int level, EventFilter filter, ProtectionRuleMap rules, ProtectionExclusions exclusions, AuthorityBehaviorMap behavior) {
        this.key = key;
        this.level = level;
        this.filter = filter;
        this.rules = rules;
        this.exclusions = exclusions;
        this.behavior = behavior;

        this.filterWithExclusions = EventFilter.and(filter, exclusions);
    }

    public static BuiltinAuthority universe() {
        return universe(ProtectionRuleMap.EMPTY, ProtectionExclusions.EMPTY, AuthorityBehaviorMap.EMPTY);
    }

    private static BuiltinAuthority universe(ProtectionRuleMap rules, ProtectionExclusions exclusions, AuthorityBehaviorMap behavior) {
        return new BuiltinAuthority("universe", UNIVERSE_LEVEL, EventFilter.universe(), rules, exclusions, behavior);
    }

    public static Codec<BuiltinAuthority> universeCodec() {
        return codec(BuiltinAuthority::universe);
    }

    public static BuiltinAuthority dimension(ResourceKey<Level> dimension) {
        return dimension(dimension, ProtectionRuleMap.EMPTY, ProtectionExclusions.EMPTY, AuthorityBehaviorMap.EMPTY);
    }

    public static BuiltinAuthority dimension(ResourceKey<Level> dimension, ProtectionRuleMap rules, ProtectionExclusions exclusions, AuthorityBehaviorMap behavior) {
        String key = dimension.identifier().getPath();
        return new BuiltinAuthority(key, DIMENSION_LEVEL, EventFilter.dimension(dimension), rules, exclusions, behavior);
    }

    public static Codec<BuiltinAuthority> dimensionCodec(ResourceKey<Level> dimension) {
        return codec((rules, exclusions, behaviors) -> dimension(dimension, rules, exclusions, behaviors));
    }

    private static Codec<BuiltinAuthority> codec(Parse parse) {
        return RecordCodecBuilder.create(i -> i.group(
                ProtectionRuleMap.CODEC.optionalFieldOf("rules", ProtectionRuleMap.EMPTY).forGetter(a -> a.rules),
                ProtectionExclusions.CODEC.optionalFieldOf("exclusions", ProtectionExclusions.EMPTY).forGetter(a -> a.exclusions),
                AuthorityBehaviorMap.CODEC.optionalFieldOf("behavior", AuthorityBehaviorMap.EMPTY).forGetter(a -> a.behavior)
        ).apply(i, parse::create));
    }

    @Override
    public BuiltinAuthority withRule(ProtectionRule rule, PermissionResult result) {
        return new BuiltinAuthority(this.key, this.level, this.filter, this.rules.with(rule, result), this.exclusions, this.behavior);
    }

    @Override
    public BuiltinAuthority withExclusions(ProtectionExclusions exclusions) {
        if (this.exclusions == exclusions) {
            return this;
        }
        return new BuiltinAuthority(this.key, this.level, this.filter, this.rules, exclusions, this.behavior);
    }

    @Override
    public Authority withBehavior(AuthorityBehaviorMap behavior) {
        if (this.behavior == behavior) {
            return this;
        }
        return new BuiltinAuthority(this.key, this.level, this.filter, this.rules, this.exclusions, behavior);
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public int level() {
        return this.level;
    }

    @Override
    public ProtectionRuleMap rules() {
        return this.rules;
    }

    @Override
    public EventFilter eventFilter() {
        return this.filterWithExclusions;
    }

    @Override
    public ProtectionExclusions exclusions() {
        return this.exclusions;
    }

    @Override
    public AuthorityBehaviorMap behavior() {
        return this.behavior;
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

    interface Parse {
        BuiltinAuthority create(ProtectionRuleMap rules, ProtectionExclusions exclusions, AuthorityBehaviorMap behaviors);
    }
}
