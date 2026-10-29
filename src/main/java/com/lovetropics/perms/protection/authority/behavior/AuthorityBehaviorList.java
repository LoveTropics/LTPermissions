package com.lovetropics.perms.protection.authority.behavior;

import com.lovetropics.perms.protection.ProtectionRuleMap;
import com.lovetropics.perms.protection.authority.behavior.config.AuthorityBehavior;
import com.lovetropics.perms.protection.authority.behavior.config.AuthorityBehaviorConfigs;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AuthorityBehaviorList {
    public static final AuthorityBehaviorList EMPTY = new AuthorityBehaviorList(List.of());

    public static final Codec<AuthorityBehaviorList> CODEC = Identifier.CODEC.listOf()
            .xmap(AuthorityBehaviorList::new, map -> map.behaviorIds);

    private final List<Identifier> behaviorIds;
    private final List<AuthorityBehavior> behaviors;

    private AuthorityBehaviorList(List<Identifier> behaviorIds) {
        this.behaviorIds = behaviorIds;
        behaviors = behaviorIds.stream()
                .map(AuthorityBehaviorConfigs.REGISTRY::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public AuthorityBehaviorList addBehavior(Identifier id) {
        if (!behaviorIds.contains(id)) {
            List<Identifier> behaviorIds = new ArrayList<>(this.behaviorIds);
            behaviorIds.add(id);
            return new AuthorityBehaviorList(behaviorIds);
        } else {
            return this;
        }
    }

    public AuthorityBehaviorList removeBehavior(Identifier id) {
        if (behaviorIds.contains(id)) {
            List<Identifier> behaviorIds = new ArrayList<>(this.behaviorIds);
            behaviorIds.remove(id);
            return new AuthorityBehaviorList(behaviorIds);
        } else {
            return this;
        }
    }

    public AuthorityBehaviorList reload() {
        return new AuthorityBehaviorList(behaviorIds);
    }

    public void onPlayerEnter(ServerPlayer player) {
        for (AuthorityBehavior behavior : behaviors) {
            behavior.onPlayerEnter(player);
        }
    }

    public void onPlayerExit(ServerPlayer player) {
        for (AuthorityBehavior behavior : behaviors) {
            behavior.onPlayerExit(player);
        }
    }

    public ProtectionRuleMap buildRules(ProtectionRuleMap overrides) {
        if (behaviors.isEmpty()) {
            return overrides;
        }
        ProtectionRuleMap rules = behaviors.getFirst().rules();
        for (int i = 1; i < behaviors.size(); i++) {
            rules = rules.withAll(behaviors.get(i).rules());
        }
        return rules.withAll(overrides);
    }

    public boolean isEmpty() {
        return behaviorIds.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AuthorityBehaviorList list && behaviorIds.equals(list.behaviorIds);
    }

    @Override
    public int hashCode() {
        return behaviorIds.hashCode();
    }
}
