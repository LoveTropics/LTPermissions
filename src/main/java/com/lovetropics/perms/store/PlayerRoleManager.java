package com.lovetropics.perms.store;

import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.override.JoinOverride;
import com.lovetropics.perms.store.db.PlayerRoleDatabase;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class PlayerRoleManager {
    private static PlayerRoleManager instance;

    private final PlayerRoleDatabase database;

    private final Map<UUID, PlayerRoleSet> onlinePlayerRoles = new Object2ObjectOpenHashMap<>();
    // Updated from main thread, but may immutable states may be fetched from any thread
    private final ConcurrentMap<UUID, RoleReader> visiblePlayerRoles = new ConcurrentHashMap<>();

    private PlayerRoleManager(PlayerRoleDatabase database) {
        this.database = database;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        instance = PlayerRoleManager.open(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PlayerRoleManager instance = PlayerRoleManager.instance;
        if (instance != null) {
            PlayerRoleManager.instance = null;
            instance.close(event.getServer());
        }
    }

    public static void onPlayerLoaded(NameAndId nameAndId) {
        PlayerRoleManager instance = PlayerRoleManager.instance;
        if (instance != null) {
            instance.onPlayerLoad(nameAndId);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerRoleManager instance = PlayerRoleManager.instance;
        if (instance != null && event.getEntity() instanceof ServerPlayer player) {
            instance.onPlayerJoined(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerRoleManager instance = PlayerRoleManager.instance;
        if (instance != null && event.getEntity() instanceof ServerPlayer player) {
            instance.onPlayerLeave(player);
        }
    }

    private static PlayerRoleManager open(MinecraftServer server) {
        try {
            Path path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("player_roles");
            PlayerRoleDatabase database = PlayerRoleDatabase.open(path);
            return new PlayerRoleManager(database);
        } catch (IOException e) {
            throw new RuntimeException("failed to open player roles database");
        }
    }

    public static PlayerRoleManager get() {
        return Objects.requireNonNull(instance, "player role manager not initialized");
    }

    public void onPlayerLoad(NameAndId nameAndId) {
        if (!onlinePlayerRoles.containsKey(nameAndId.id())) {
            // Load it, but delay initialization for now, as the player connection isn't quite ready
            PlayerRoleSet newRoles = new PlayerRoleSet(RolesConfig.get().everyone());
            onlinePlayerRoles.put(nameAndId.id(), newRoles);
            visiblePlayerRoles.put(nameAndId.id(), newRoles.copy());
            database.tryLoadInto(nameAndId.id(), newRoles);
        }
    }

    public void onPlayerJoined(ServerPlayer player) {
        PlayerRoleSet roles = onlinePlayerRoles.get(player.getUUID());
        if (roles == null) {
            // Load it, but delay initialization for now, as the player connection isn't quite ready
            roles = new PlayerRoleSet(RolesConfig.get().everyone());
            onlinePlayerRoles.put(player.getUUID(), roles);
            visiblePlayerRoles.put(player.getUUID(), roles.copy());
            database.tryLoadInto(player.getUUID(), roles);
        }
        roles.attachPlayer(player, true);
    }

    public void onPlayerLeave(ServerPlayer player) {
        PlayerRoleSet roles = onlinePlayerRoles.remove(player.getUUID());
        visiblePlayerRoles.remove(player.getUUID());
        if (roles != null && roles.isDirty()) {
            database.trySave(player.getUUID(), roles);
            roles.setDirty(false);
        }
    }

    public void onRoleReload(MinecraftServer server, RolesConfig config) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerRoleSet oldRoles = onlinePlayerRoles.get(player.getUUID());

            PlayerRoleSet newRoles = new PlayerRoleSet(config.everyone());
            if (oldRoles != null) {
                newRoles.reloadFrom(config, oldRoles);

                boolean couldJoinBefore = oldRoles.overrides().get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT).allowsJoining();
                JoinOverride newJoinOverride = newRoles.overrides().get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT);
                boolean canJoinNow = newJoinOverride.allowsJoining();
                Optional<Component> enforce = newJoinOverride.enforce();
                if (couldJoinBefore && !canJoinNow && enforce.isPresent()) {
                    player.connection.disconnect(enforce.get());
                }
            }

            onlinePlayerRoles.put(player.getUUID(), newRoles);
            visiblePlayerRoles.put(player.getUUID(), newRoles.copy());

            newRoles.attachPlayer(player, false);
        }
    }

    private void close(MinecraftServer server) {
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerLeave(player);
            }
        } finally {
            IOUtils.closeQuietly(database);
        }
    }

    public <R> R updateRoles(UUID uuid, Function<PlayerRoleSet, R> update) {
        PlayerRoleSet roles = onlinePlayerRoles.get(uuid);
        if (roles != null) {
            R result = update.apply(roles);
            visiblePlayerRoles.put(uuid, roles.copy());
            return result;
        } else {
            roles = loadOfflinePlayerRoles(uuid);

            try {
                return update.apply(roles);
            } finally {
                if (roles.isDirty()) {
                    database.trySave(uuid, roles);
                }
            }
        }
    }

    public RoleReader peekRoles(UUID uuid) {
        RoleReader roles = visiblePlayerRoles.get(uuid);
        return roles != null ? roles : loadOfflinePlayerRoles(uuid).copy();
    }

    private PlayerRoleSet loadOfflinePlayerRoles(UUID uuid) {
        RolesConfig config = RolesConfig.get();

        PlayerRoleSet roles = new PlayerRoleSet(config.everyone());
        database.tryLoadInto(uuid, roles);

        return roles;
    }

    @Nullable
    public RoleReader getRolesForOnline(ServerPlayer player) {
        return visiblePlayerRoles.get(player.getUUID());
    }

    public List<UUID> listPlayersWithRole(Role role) {
        List<UUID> playerIds = new ArrayList<>();
        RolesConfig config = RolesConfig.get();
        for (UUID uuid : database.knownPlayerIds()) {
            PlayerRoleSet roles = onlinePlayerRoles.get(uuid);
            if (roles == null) {
                roles = new PlayerRoleSet(config.everyone());
                database.tryLoadInto(uuid, roles);
            }
            if (roles.has(role)) {
                playerIds.add(uuid);
            }
        }
        return playerIds;
    }

    public int countOnlinePlayersWith(Role role) {
        int count = 0;
        for (PlayerRoleSet roles : onlinePlayerRoles.values()) {
            if (roles.has(role)) {
                count++;
            }
        }
        return count;
    }
}
