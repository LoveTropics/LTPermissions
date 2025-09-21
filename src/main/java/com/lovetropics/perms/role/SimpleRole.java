package com.lovetropics.perms.role;

import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.perms.override.RoleOverrideMap;

public record SimpleRole(String id, RoleOverrideMap overrides, int index) implements Role {
    public static SimpleRole empty(String id) {
        return new SimpleRole(id, RoleOverrideMap.EMPTY, 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj instanceof SimpleRole role && this.index == role.index && role.id.equalsIgnoreCase(this.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "\"" + this.id + "\" (" + this.index + ")";
    }
}
