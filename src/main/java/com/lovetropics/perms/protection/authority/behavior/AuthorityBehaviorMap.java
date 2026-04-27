package com.lovetropics.perms.protection.authority.behavior;

import com.google.common.collect.ImmutableList;
import com.lovetropics.perms.protection.authority.behavior.config.AuthorityBehaviorConfig;
import com.lovetropics.perms.protection.authority.behavior.config.AuthorityBehaviorConfigs;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class AuthorityBehaviorMap {
    public static final AuthorityBehaviorMap EMPTY = new AuthorityBehaviorMap(ImmutableList.of());

    public static final Codec<AuthorityBehaviorMap> CODEC = Identifier.CODEC.listOf()
            .xmap(AuthorityBehaviorMap::new, map -> map.behaviorIds);

    private final List<Identifier> behaviorIds;
    private final AuthorityBehavior behavior;

    private AuthorityBehaviorMap(List<Identifier> behaviorIds) {
        this.behaviorIds = behaviorIds;
        this.behavior = this.buildBehavior();
    }

    private AuthorityBehavior buildBehavior() {
        if (!this.behaviorIds.isEmpty()) {
            List<AuthorityBehavior> behaviors = new ArrayList<>();
            for (Identifier id : this.behaviorIds) {
                AuthorityBehaviorConfig config = AuthorityBehaviorConfigs.REGISTRY.get(id);
                if (config != null) {
                    behaviors.add(config.createBehavior());
                }
            }

            if (behaviors.size() == 1) {
                return behaviors.get(0);
            } else if (!behaviors.isEmpty()) {
                return AuthorityBehavior.compose(behaviors.toArray(new AuthorityBehavior[0]));
            }
        }

        return AuthorityBehavior.EMPTY;
    }

    public AuthorityBehaviorMap addBehavior(Identifier id) {
        if (!this.behaviorIds.contains(id)) {
            List<Identifier> behaviorIds = new ArrayList<>(this.behaviorIds);
            behaviorIds.add(id);
            return new AuthorityBehaviorMap(behaviorIds);
        } else {
            return this;
        }
    }

    public AuthorityBehaviorMap removeBehavior(Identifier id) {
        if (this.behaviorIds.contains(id)) {
            List<Identifier> behaviorIds = new ArrayList<>(this.behaviorIds);
            behaviorIds.remove(id);
            return new AuthorityBehaviorMap(behaviorIds);
        } else {
            return this;
        }
    }

    public AuthorityBehaviorMap rebuild() {
        return new AuthorityBehaviorMap(this.behaviorIds);
    }

    public AuthorityBehavior getBehavior() {
        return this.behavior;
    }

    public List<Identifier> getBehaviorIds() {
        return this.behaviorIds;
    }

    public boolean isEmpty() {
        return this.behaviorIds.isEmpty();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof AuthorityBehaviorMap map && behaviorIds.equals(map.behaviorIds);
    }

    @Override
    public int hashCode() {
        return behaviorIds.hashCode();
    }
}
