package com.lovetropics.perms.mixin;

import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.override.JoinOverride;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    protected int maxPlayers;

    @Shadow
    @Final
    private IpBanList ipBans;

    @Inject(method = "load", at = @At("RETURN"))
    private void load(ServerPlayer player, ProblemReporter problemReporter, CallbackInfoReturnable<Optional<ValueInput>> cir) {
        PlayerRoleManager.onPlayerLoaded(player);
    }

    @Inject(method = "isOp", at = @At("HEAD"), cancellable = true)
    private void checkIsOp(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(profile.getId());
        Integer opLevel = roles.overrides().getOrNull(LTPermissions.OP_LEVEL);
        if (opLevel != null && opLevel >= 4) {
            cir.setReturnValue(true);
        }
    }

    // Basically replace canPlayerLogin check with our own implementation for more control
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void canJoin(SocketAddress socketAddress, GameProfile gameProfile, CallbackInfoReturnable<Component> cir) {
        // Note not adding support for Expires on bans for simplicity (Not even supported with commands anyway)
        if (bans.isBanned(gameProfile)) {
            cir.setReturnValue(Component.translatable("multiplayer.disconnect.banned.reason", this.bans.get(gameProfile).getReason()));
            return;
        }
        if (ipBans.isBanned(socketAddress)) {
            cir.setReturnValue(Component.translatable("multiplayer.disconnect.banned_ip.reason", ipBans.get(socketAddress).getReason()));
            return;
        }

        int playersOnline = players.size();
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(gameProfile.getId());

        JoinOverride userOverrides = roles
                .overrides()
                .get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT);

        if (!userOverrides.allowsJoining()) {
            cir.setReturnValue(userOverrides.notWhitelistedMessage());
            return;
        }
        if (userOverrides.byPassJoinLimit()) {
            cir.setReturnValue(null);
            return;
        }
        if (playersOnline >= maxPlayers) {
            cir.setReturnValue(Component.translatable("multiplayer.disconnect.server_full"));
            return;
        }
        if (userOverrides.joinLimit().isPresent()) {
            for (Role role : roles) {
                JoinOverride roleJoinOverride = role.overrides().get(LTPermissions.JOIN_ACCESS, JoinOverride.DEFAULT);
                if (roleJoinOverride.joinLimit().isPresent()) {
                    JoinOverride.JoinData joinData = roleJoinOverride.joinLimit().get();
                    int onlineCount = PlayerRoleManager.get().countOnlinePlayersWith(role);
                    if (onlineCount >= joinData.limit()) {
                        cir.setReturnValue(joinData.message());
                        return;
                    }
                }
            }
        }
    }
}
