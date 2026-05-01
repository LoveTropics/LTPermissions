package com.lovetropics.perms.mixin;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true)
    private void checkPermissions(NameAndId nameAndId, CallbackInfoReturnable<LevelBasedPermissionSet> cir) {
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(nameAndId.id());
        PermissionLevel permissionLevel = roles.overrides().getOrNull(LTPermissions.OP_LEVEL);
        if (permissionLevel != null) {
            cir.setReturnValue(LevelBasedPermissionSet.forLevel(permissionLevel));
        }
    }
}

