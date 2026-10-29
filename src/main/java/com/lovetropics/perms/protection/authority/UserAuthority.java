package com.lovetropics.perms.protection.authority;

import com.lovetropics.lib.permission.PermissionResult;
import com.lovetropics.perms.protection.EventFilter;
import com.lovetropics.perms.protection.ProtectionExclusions;
import com.lovetropics.perms.protection.ProtectionRule;
import com.lovetropics.perms.protection.ProtectionRuleMap;
import com.lovetropics.perms.protection.authority.behavior.AuthorityBehaviorList;
import com.lovetropics.perms.protection.authority.shape.AuthorityShape;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class UserAuthority implements Authority {
    public static final Codec<UserAuthority> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("key").forGetter(a -> a.key),
            Codec.INT.fieldOf("level").forGetter(a -> a.level),
            AuthorityShapes.CODEC.fieldOf("shapes").forGetter(a -> a.shapes),
            ProtectionRuleMap.CODEC.fieldOf("rules").forGetter(a -> a.ruleOverrides),
            ProtectionExclusions.CODEC.fieldOf("exclusions").forGetter(a -> a.exclusions),
            AuthorityBehaviorList.CODEC.fieldOf("behavior").forGetter(a -> a.behavior)
    ).apply(i, UserAuthority::new));

    private final String key;
    private final int level;
    private final AuthorityShapes shapes;
    private final ProtectionRuleMap composedRules;
    private final ProtectionRuleMap ruleOverrides;
    private final ProtectionExclusions exclusions;
    private final AuthorityBehaviorList behavior;

    private final EventFilter filterWithExclusions;

    UserAuthority(String key, int level, AuthorityShapes shapes, ProtectionRuleMap ruleOverrides, ProtectionExclusions exclusions, AuthorityBehaviorList behavior) {
        this.key = key;
        this.level = level;
        this.shapes = shapes;
        composedRules = behavior.buildRules(ruleOverrides);
        this.ruleOverrides = ruleOverrides;
        this.exclusions = exclusions;
        this.behavior = behavior;

        this.filterWithExclusions = EventFilter.and(shapes, exclusions);
    }

    UserAuthority(String key, int level, AuthorityShapes shapes) {
        this(key, level, shapes, ProtectionRuleMap.EMPTY, ProtectionExclusions.EMPTY, AuthorityBehaviorList.EMPTY);
    }

    public static UserAuthority create(String key) {
        return new UserAuthority(key, 0, new AuthorityShapes());
    }

    @Override
    public UserAuthority withRuleOverride(ProtectionRule rule, PermissionResult result) {
        return new UserAuthority(this.key, this.level, this.shapes, this.ruleOverrides.with(rule, result), this.exclusions, this.behavior);
    }

    @Override
    public UserAuthority withExclusions(ProtectionExclusions exclusions) {
        if (this.exclusions == exclusions) {
            return this;
        }
        return new UserAuthority(this.key, this.level, this.shapes, this.ruleOverrides, exclusions, this.behavior);
    }

    @Override
    public Authority withBehavior(AuthorityBehaviorList behavior) {
        if (this.behavior == behavior) {
            return this;
        }
        return new UserAuthority(this.key, this.level, this.shapes, this.ruleOverrides, this.exclusions, behavior);
    }

    public UserAuthority withLevel(int level) {
        return new UserAuthority(this.key, level, this.shapes, this.ruleOverrides, this.exclusions, this.behavior);
    }

    public UserAuthority addShape(String key, AuthorityShape shape) {
        return this.withShape(this.shapes.addShape(key, shape));
    }

    public UserAuthority removeShape(String key) {
        return this.withShape(this.shapes.removeShape(key));
    }

    private UserAuthority withShape(AuthorityShapes shape) {
        if (this.shapes == shape) {
            return this;
        }
        return new UserAuthority(this.key, this.level, shape, this.ruleOverrides, this.exclusions, this.behavior);
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
        return this.composedRules;
    }

    @Override
    public ProtectionRuleMap ruleOverrides() {
        return this.ruleOverrides;
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
    public AuthorityBehaviorList behavior() {
        return this.behavior;
    }

    public AuthorityShapes shape() {
        return this.shapes;
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }
}
