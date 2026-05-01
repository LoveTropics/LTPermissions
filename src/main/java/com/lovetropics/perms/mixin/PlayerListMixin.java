package com.lovetropics.perms.mixin;

import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.override.JoinOverride;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.net.SocketAddress;
import java.util.List;
import java.util.Optional;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow
    @Final
    private UserBanList bans;

    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Shadow
    @Final
    private IpBanList ipBans;

    @Shadow
    public abstract MinecraftServer getServer();

    @Shadow
    public abstract int getMaxPlayers();

    @Inject(method = "loadPlayerData", at = @At("RETURN"))
    private void load(NameAndId nameAndId, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        PlayerRoleManager.onPlayerLoaded(nameAndId);
    }

    @Inject(method = "isOp", at = @At("HEAD"), cancellable = true)
    private void checkIsOp(NameAndId nameAndId, CallbackInfoReturnable<Boolean> cir) {
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(nameAndId.id());
        PermissionLevel opLevel = roles.overrides().getOrNull(LTPermissions.OP_LEVEL);
        if (opLevel != null && opLevel.isEqualOrHigherThan(PermissionLevel.OWNERS)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * @author LTPermissions
     * @reason Custom join access control
     * Note returning null means the player can join
     */
    @Nullable
    @Overwrite
    public Component canPlayerLogin(SocketAddress socketAddress, NameAndId gameProfile) {
        if (getServer().isSingleplayer()) {
            return null;
        }
        // Note not adding support for Expires on bans for simplicity (Not even supported with commands anyway)
        if (bans.isBanned(gameProfile)) {
            return Component.translatable("multiplayer.disconnect.banned.reason", this.bans.get(gameProfile).getReason());
        }
        if (ipBans.isBanned(socketAddress)) {
            return Component.translatable("multiplayer.disconnect.banned_ip.reason", ipBans.get(socketAddress).getReason());
        }

        int playersOnline = players.size();
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(gameProfile.id());

        JoinOverride userOverrides = roles
                .overrides()
                .get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT);

        if (!userOverrides.allowsJoining()) {
            return userOverrides.notWhitelistedMessage();
        }
        if (userOverrides.byPassJoinLimit()) {
            return null;
        }
        if (playersOnline >= getMaxPlayers()) {
            return Component.translatable("multiplayer.disconnect.server_full");
        }
        if (userOverrides.joinLimit().isPresent()) {
            for (Role role : roles) {
                JoinOverride roleJoinOverride = role.overrides().get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT);
                if (roleJoinOverride.joinLimit().isPresent()) {
                    JoinOverride.JoinData joinData = roleJoinOverride.joinLimit().get();
                    int onlineCount = PlayerRoleManager.get().countOnlinePlayersWith(role);
                    if (onlineCount >= joinData.limit()) {
                        return joinData.message();
                    }
                }
            }
        }
        return null;
    }
}
