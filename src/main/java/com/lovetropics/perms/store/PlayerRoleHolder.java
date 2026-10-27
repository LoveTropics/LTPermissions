package com.lovetropics.perms.store;

import com.google.common.collect.Sets;
import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.lib.permission.role.RoleOverrideReader;
import com.lovetropics.lib.permission.role.RoleOverrideType;
import com.lovetropics.lib.permission.role.RoleProvider;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.override.JoinOverride;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;

public final class PlayerRoleHolder {
    private Role everyoneRole;
    private final MutableRoleSet roles = new MutableRoleSet();
    private @Nullable ServerPlayer player;

    // Visible to multiple threads, only updated from main thread
    private volatile RoleReader reader;
    private boolean dirty;

    public PlayerRoleHolder(Role everyoneRole) {
        this.everyoneRole = everyoneRole;
        reader = roles.bakeReader(everyoneRole);
    }

    public RoleReader asReader() {
        return reader;
    }

    public void attachPlayer(ServerPlayer player) {
        this.player = player;
        for (RoleOverrideType<?> overrideType : reader.overrides().typeSet()) {
            overrideType.notifyInitialize(player);
        }
    }

    private void rebuildAndNotify() {
        RoleReader oldReader = reader;
        reader = roles.bakeReader(everyoneRole);
        if (player != null) {
            notifyChange(player, oldReader.overrides(), reader.overrides());
        }
    }

    private void notifyChange(ServerPlayer player, RoleOverrideReader oldOverrides, RoleOverrideReader newOverrides) {
        boolean couldJoinBefore = oldOverrides.get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT).allowsJoining();
        JoinOverride newJoinOverride = newOverrides.get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT);
        boolean canJoinNow = newJoinOverride.allowsJoining();
        Optional<Component> enforce = newJoinOverride.enforce();
        // TODO: Should be a change listener?
        if (couldJoinBefore && !canJoinNow && enforce.isPresent()) {
            player.connection.disconnect(enforce.get());
        }

        for (RoleOverrideType<?> override : Sets.union(newOverrides.typeSet(), oldOverrides.typeSet())) {
            override.notifyChange(player);
        }
    }

    public boolean add(Role role) {
        if (roles.add(role)) {
            dirty = true;
            rebuildAndNotify();
            return true;
        }
        return false;
    }

    public boolean remove(Role role) {
        if (roles.remove(role)) {
            dirty = true;
            rebuildAndNotify();
            return true;
        }
        return false;
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isEmpty() {
        return roles.isEmpty();
    }

    public PackedRoles pack() {
        return roles.pack();
    }

    public void unpackFrom(RoleProvider roleProvider, PackedRoles roles) {
        this.roles.unpackFrom(roleProvider, roles);
        reader = this.roles.bakeReader(everyoneRole);
    }

    public void reloadFor(Role everyoneRole, RoleProvider roleProvider) {
        PackedRoles oldRoles = roles.pack();
        if (roles.unpackFrom(roleProvider, oldRoles)) {
            // A role has been removed, resave without it present
            dirty = true;
        }
        this.everyoneRole = everyoneRole;
        rebuildAndNotify();
    }
}
