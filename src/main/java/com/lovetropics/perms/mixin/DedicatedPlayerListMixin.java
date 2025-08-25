package com.lovetropics.perms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DedicatedPlayerList.class)
public abstract class DedicatedPlayerListMixin extends PlayerList {
    public DedicatedPlayerListMixin(MinecraftServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo, int maxPlayers) {
        super(server, registries, playerIo, maxPlayers);
    }

    @WrapOperation(method = "isWhiteListed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/UserWhiteList;isWhiteListed(Lcom/mojang/authlib/GameProfile;)Z"))
    private boolean isWhiteListed(UserWhiteList whiteList, GameProfile profile, Operation<Boolean> original) {
        if (original.call(whiteList, profile)) {
            return true;
        }
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(profile.getId());
        return roles.overrides().test(LTPermissions.BYPASS_WHITELIST);
    }
}
