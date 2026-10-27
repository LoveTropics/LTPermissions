package com.lovetropics.perms.store;

import com.google.common.collect.Iterators;
import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.lib.permission.role.RoleOverrideReader;
import com.lovetropics.lib.permission.role.RoleProvider;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.override.RoleOverrideMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;

import java.util.Iterator;
import java.util.stream.Stream;

public final class MutableRoleSet implements Iterable<Role> {
    private final ObjectSortedSet<Role> roles = new ObjectAVLTreeSet<>();

    public RoleReader bakeReader(Role everyoneRole) {
        ObjectAVLTreeSet<Role> roles = new ObjectAVLTreeSet<>(this.roles);
        RoleOverrideMap overrides = buildOverrides();
        return new RoleReader() {
            @Override
            public Iterator<Role> iterator() {
                return Iterators.concat(
                        roles.iterator(),
                        Iterators.singletonIterator(everyoneRole)
                );
            }

            @Override
            public Stream<Role> stream() {
                return Stream.concat(
                        roles.stream(),
                        Stream.of(everyoneRole)
                );
            }

            @Override
            public boolean has(Role role) {
                return role == everyoneRole || roles.contains(role);
            }

            @Override
            public RoleOverrideReader overrides() {
                return overrides;
            }
        };
    }

    private RoleOverrideMap buildOverrides() {
        RoleOverrideMap.Builder builder = RoleOverrideMap.builder();
        for (Role role : this) {
            builder.addAll(role.overrides());
        }
        return builder.build();
    }

    @Override
    public Iterator<Role> iterator() {
        return roles.iterator();
    }

    public boolean add(Role role) {
        return roles.add(role);
    }

    public boolean remove(Role role) {
        return roles.remove(role);
    }

    public boolean contains(Role role) {
        return roles.contains(role);
    }

    public boolean isEmpty() {
        return roles.isEmpty();
    }

    public PackedRoles pack() {
        return new PackedRoles(roles.stream().map(Role::id).toList());
    }

    public boolean unpackFrom(RoleProvider roleProvider, PackedRoles packed) {
        boolean anyInvalid = false;
        roles.clear();
        for (String name : packed.roles()) {
            Role role = roleProvider.get(name);
            if (role == null || name.equalsIgnoreCase(Role.EVERYONE)) {
                LTPermissions.LOGGER.warn("Encountered invalid role '{}'", name);
                anyInvalid = true;
                continue;
            }
            roles.add(role);
        }
        return anyInvalid;
    }
}
