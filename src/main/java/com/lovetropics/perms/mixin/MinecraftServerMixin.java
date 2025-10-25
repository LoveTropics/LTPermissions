package com.lovetropics.perms.mixin;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true)
    private void checkPermissions(GameProfile profile, CallbackInfoReturnable<Integer> cir) {
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(profile.getId());
        Integer opLevel = roles.overrides().getOrNull(LTPermissions.OP_LEVEL);
        if (opLevel != null) {
            cir.setReturnValue(opLevel);
        }
    }
}

