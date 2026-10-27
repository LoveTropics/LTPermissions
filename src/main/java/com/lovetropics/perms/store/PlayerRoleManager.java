package com.lovetropics.perms.store;

import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.store.db.PlayerRoleDatabase;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class PlayerRoleManager {
    private static @Nullable PlayerRoleManager instance;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final PlayerRoleDatabase database;

    // Updated from main thread only
    private final ConcurrentMap<UUID, PlayerRoleHolder> onlinePlayerHolders = new ConcurrentHashMap<>();

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
        if (!onlinePlayerHolders.containsKey(nameAndId.id())) {
            // Load it, but delay initialization for now, as the player connection isn't quite ready
            loadPlayer(nameAndId.id());
        }
    }

    public void onPlayerJoined(ServerPlayer player) {
        PlayerRoleHolder holder = onlinePlayerHolders.get(player.getUUID());
        if (holder == null) {
            holder = loadPlayer(player.getUUID());
        }
        holder.attachPlayer(player);
    }

    private PlayerRoleHolder loadPlayer(UUID playerId) {
        PackedRoles packedRoles = database.tryLoad(playerId).orElse(PackedRoles.EMPTY);

        PlayerRoleHolder holder = new PlayerRoleHolder(RolesConfig.get().everyone());
        holder.unpackFrom(RolesConfig.get(), packedRoles);
        onlinePlayerHolders.put(playerId, holder);

        return holder;
    }

    public void onPlayerLeave(ServerPlayer player) {
        PlayerRoleHolder holder = onlinePlayerHolders.remove(player.getUUID());
        if (holder != null && holder.isDirty()) {
            database.trySave(player.getUUID(), holder.pack());
            holder.setDirty(false);
        }
    }

    public void onRoleReload(MinecraftServer server, RolesConfig config) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerRoleHolder holder = onlinePlayerHolders.get(player.getUUID());
            if (holder == null) {
                LOGGER.warn("Unable to reload roles for unregistered player: {}", player.getScoreboardName());
                continue;
            }
            holder.reloadFor(config.everyone(), config);
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

    public <R> R updateRoles(UUID uuid, Function<PlayerRoleHolder, R> update) {
        PlayerRoleHolder holder = onlinePlayerHolders.get(uuid);
        if (holder != null) {
            return update.apply(holder);
        } else {
            holder = loadOfflinePlayerRoles(uuid);

            try {
                return update.apply(holder);
            } finally {
                if (holder.isDirty()) {
                    database.trySave(uuid, holder.pack());
                }
            }
        }
    }

    public RoleReader peekRoles(UUID uuid) {
        PlayerRoleHolder holder = onlinePlayerHolders.get(uuid);
        return holder != null ? holder.asReader() : loadOfflinePlayerRoles(uuid).asReader();
    }

    private PlayerRoleHolder loadOfflinePlayerRoles(UUID uuid) {
        PackedRoles packedRoles = database.tryLoad(uuid).orElse(PackedRoles.EMPTY);
        RolesConfig config = RolesConfig.get();
        PlayerRoleHolder holder = new PlayerRoleHolder(config.everyone());
        holder.unpackFrom(config, packedRoles);
        return holder;
    }

    public @Nullable RoleReader getRolesForOnline(ServerPlayer player) {
        PlayerRoleHolder holder = onlinePlayerHolders.get(player.getUUID());
        return holder != null ? holder.asReader() : null;
    }

    public List<UUID> listPlayersWithRole(Role role) {
        List<UUID> playerIds = new ArrayList<>();
        for (UUID uuid : database.knownPlayerIds()) {
            PlayerRoleHolder holder = onlinePlayerHolders.get(uuid);
            if (holder != null) {
                if (holder.has(role)) {
                    playerIds.add(uuid);
                }
            } else {
                PackedRoles packedRoles = database.tryLoad(uuid).orElse(PackedRoles.EMPTY);
                if (packedRoles.roles().contains(role.id())) {
                    playerIds.add(uuid);
                }
            }
        }
        return playerIds;
    }

    public int countOnlinePlayersWith(Role role) {
        int count = 0;
        for (PlayerRoleHolder roles : onlinePlayerHolders.values()) {
            if (roles.has(role)) {
                count++;
            }
        }
        return count;
    }
}
