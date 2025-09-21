package com.lovetropics.perms.protection;

import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.Role;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class ProtectionExclusions implements EventFilter {
    public static final ProtectionExclusions EMPTY = new ProtectionExclusions();

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<ProtectionExclusions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("roles").forGetter(exclusions -> new ArrayList<>(exclusions.roles)),
            UUID_CODEC.listOf().fieldOf("players").forGetter(exclusions -> new ArrayList<>(exclusions.players)),
            Codec.BOOL.optionalFieldOf("operators", true).forGetter(exclusions -> exclusions.operators)
    ).apply(i, ProtectionExclusions::new));

    private final Set<String> roles;
    private final Set<UUID> players;
    private final boolean operators;

    private ProtectionExclusions() {
        this.roles = new ObjectOpenHashSet<>();
        this.players = new ObjectOpenHashSet<>();
        this.operators = true;
    }

    private ProtectionExclusions(Collection<String> roles, Collection<UUID> players, boolean operators) {
        this.roles = new ObjectOpenHashSet<>(roles);
        this.players = new ObjectOpenHashSet<>(players);
        this.operators = operators;
    }

    public ProtectionExclusions addRole(Role role) {
        if (this.roles.contains(role.id())) {
            return this;
        }

        ProtectionExclusions result = new ProtectionExclusions(this.roles, this.players, this.operators);
        result.roles.add(role.id());
        return result;
    }

    public ProtectionExclusions removeRole(Role role) {
        if (!this.roles.contains(role.id())) {
            return this;
        }

        ProtectionExclusions result = new ProtectionExclusions(this.roles, this.players, this.operators);
        result.roles.remove(role.id());
        return result;
    }

    public ProtectionExclusions addPlayer(GameProfile profile) {
        if (this.players.contains(profile.getId())) {
            return this;
        }

        ProtectionExclusions result = new ProtectionExclusions(this.roles, this.players, this.operators);
        result.players.add(profile.getId());
        return result;
    }

    public ProtectionExclusions removePlayer(GameProfile profile) {
        if (!this.players.contains(profile.getId())) {
            return this;
        }

        ProtectionExclusions result = new ProtectionExclusions(this.roles, this.players, this.operators);
        result.players.remove(profile.getId());
        return result;
    }

    public ProtectionExclusions withOperators(boolean operators) {
        if (this.operators == operators) {
            return this;
        }

        return new ProtectionExclusions(this.roles, this.players, operators);
    }

    public boolean isExcluded(Player player) {
        if (this.players.contains(player.getUUID())) {
            return true;
        }

        if (player instanceof ServerPlayer) {
            if (this.operators && player.hasPermissions(4)) {
                return true;
            }

            for (String excludeRoleId : this.roles) {
                Role excludeRole = PermissionsApi.provider().get(excludeRoleId);
                if (excludeRole != null && PermissionsApi.lookup().byPlayer(player).has(excludeRole)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean accepts(EventSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof Player) {
            return !this.isExcluded((Player) entity);
        } else {
            return true;
        }
    }

    public boolean isEmpty() {
        return this.players.isEmpty() && this.roles.isEmpty() && this.operators;
    }
}
