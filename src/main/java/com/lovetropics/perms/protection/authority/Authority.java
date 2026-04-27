package com.lovetropics.perms.protection.authority;

import com.lovetropics.lib.permission.PermissionResult;
import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.perms.protection.EventFilter;
import com.lovetropics.perms.protection.ProtectionExclusions;
import com.lovetropics.perms.protection.ProtectionRule;
import com.lovetropics.perms.protection.ProtectionRuleMap;
import com.lovetropics.perms.protection.authority.behavior.AuthorityBehaviorMap;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;

public interface Authority extends Comparable<Authority> {
    String key();

    int level();

    ProtectionRuleMap rules();

    EventFilter eventFilter();

    ProtectionExclusions exclusions();

    AuthorityBehaviorMap behavior();

    default boolean hasBehavior() {
        return !this.behavior().isEmpty();
    }

    Authority withRule(ProtectionRule rule, PermissionResult result);

    Authority withExclusions(ProtectionExclusions exclusions);

    Authority withBehavior(AuthorityBehaviorMap behavior);

    default Authority addExclusion(NameAndId player) {
        return this.withExclusions(this.exclusions().addPlayer(player));
    }

    default Authority addExclusion(Role role) {
        return this.withExclusions(this.exclusions().addRole(role));
    }

    default Authority removeExclusion(NameAndId player) {
        return this.withExclusions(this.exclusions().removePlayer(player));
    }

    default Authority removeExclusion(Role role) {
        return this.withExclusions(this.exclusions().removeRole(role));
    }

    default Authority excludeOperators(boolean operators) {
        return this.withExclusions(this.exclusions().withOperators(operators));
    }

    default Authority addBehavior(Identifier id) {
        return this.withBehavior(this.behavior().addBehavior(id));
    }

    default Authority removeBehavior(Identifier id) {
        return this.withBehavior(this.behavior().removeBehavior(id));
    }

    default boolean isEmpty() {
        return this.rules().isEmpty() && this.exclusions().isEmpty() && this.behavior().isEmpty();
    }

    @Override
    default int compareTo(Authority other) {
        int levelCompare = Integer.compare(other.level(), this.level());
        if (levelCompare != 0) {
            return levelCompare;
        } else {
            return this.key().compareTo(other.key());
        }
    }
}
