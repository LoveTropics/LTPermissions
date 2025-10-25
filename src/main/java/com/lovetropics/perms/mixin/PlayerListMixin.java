package com.lovetropics.perms.mixin;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerList.class)
public class PlayerListMixin {
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
}
