package com.lovetropics.perms.mixin.compact;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//https://github.com/EngineHub/WorldEdit/blob/version/7.3.x/worldedit-neoforge/src/main/java/com/sk89q/worldedit/neoforge/NeoForgePermissionsProvider.java#L32
@Mixin(targets = "com.sk89q.worldedit.neoforge.NeoForgePermissionsProvider$VanillaPermissionsProvider")
public class WorldEditNeoForgePermissionsProviderMixin {
    @Inject(method = "hasPermission", at = @At("HEAD"), cancellable = true)
    private void hasPermission(ServerPlayer player, String permission, CallbackInfoReturnable<Boolean> cir) {
        PlayerRoleSet roles = PlayerRoleManager.get().peekRoles(player.getUUID());
        if (roles.overrides().test(LTPermissions.WORLDEDIT_ALLOWED)) {
            cir.setReturnValue(true);
        }
    }
}
