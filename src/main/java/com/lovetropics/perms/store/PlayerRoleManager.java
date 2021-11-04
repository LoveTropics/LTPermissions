package com.lovetropics.perms.store;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.role.RoleReader;
import com.lovetropics.perms.store.db.PlayerRoleDatabase;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = LTPermissions.ID)
public final class PlayerRoleManager {
    private static PlayerRoleManager instance;

    private final PlayerRoleDatabase database;

    private final Map<UUID, PlayerRoleSet> onlinePlayerRoles = new Object2ObjectOpenHashMap<>();

    private PlayerRoleManager(PlayerRoleDatabase database) {
        this.database = database;
    }

    @SubscribeEvent
    public static void onServerStarting(FMLServerStartingEvent event) {
        instance = PlayerRoleManager.open(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(FMLServerStoppedEvent event) {
        PlayerRoleManager instance = PlayerRoleManager.instance;
        if (instance != null) {
            PlayerRoleManager.instance = null;
            instance.close(event.getServer());
        }
    }

    private static PlayerRoleManager open(MinecraftServer server) {
        try {
            Path path = server.func_240776_a_(FolderName.PLAYERDATA).resolve("player_roles");
            PlayerRoleDatabase database = PlayerRoleDatabase.open(path);
            return new PlayerRoleManager(database);
        } catch (IOException e) {
            throw new RuntimeException("failed to open player roles database");
        }
    }

    public static PlayerRoleManager get() {
        return Objects.requireNonNull(instance, "player role manager not initialized");
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        RolesConfig config = RolesConfig.get();
        PlayerRoleSet roles = this.loadPlayerRoles(player, config);
        this.database.tryLoadInto(player.getUniqueID(), roles);
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        PlayerRoleSet roles = this.onlinePlayerRoles.remove(player.getUniqueID());
        if (roles != null && roles.isDirty()) {
            this.database.trySave(player.getUniqueID(), roles);
            roles.setDirty(false);
        }
    }

    public void onRoleReload(MinecraftServer server, RolesConfig config) {
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            this.loadPlayerRoles(player, config);
        }
    }

    private PlayerRoleSet loadPlayerRoles(ServerPlayerEntity player, RolesConfig config) {
        PlayerRoleSet oldRoles = this.onlinePlayerRoles.get(player.getUniqueID());

        PlayerRoleSet newRoles = new PlayerRoleSet(config.everyone(), player);
        if (oldRoles != null) {
            newRoles.reloadFrom(config, oldRoles);
            newRoles.rebuildOverridesAndNotify();
        }

        this.onlinePlayerRoles.put(player.getUniqueID(), newRoles);

        return newRoles;
    }

    private void close(MinecraftServer server) {
        try {
            for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
                this.onPlayerLeave(player);
            }
        } finally {
            IOUtils.closeQuietly(this.database);
        }
    }

    public <R> R updateRoles(UUID uuid, Function<PlayerRoleSet, R> update) {
        PlayerRoleSet roles = this.onlinePlayerRoles.get(uuid);
        if (roles != null) {
            return update.apply(roles);
        } else {
            roles = this.loadOfflinePlayerRoles(uuid);

            try {
                return update.apply(roles);
            } finally {
                if (roles.isDirty()) {
                    this.database.trySave(uuid, roles);
                }
            }
        }
    }

    public PlayerRoleSet peekRoles(UUID uuid) {
        PlayerRoleSet roles = this.onlinePlayerRoles.get(uuid);
        return roles != null ? roles : this.loadOfflinePlayerRoles(uuid);
    }

    private PlayerRoleSet loadOfflinePlayerRoles(UUID uuid) {
        RolesConfig config = RolesConfig.get();

        PlayerRoleSet roles = new PlayerRoleSet(config.everyone(), null);
        this.database.tryLoadInto(uuid, roles);

        return roles;
    }

    @Nullable
    public RoleReader getRolesForOnline(ServerPlayerEntity player) {
        return this.onlinePlayerRoles.get(player.getUniqueID());
    }
}