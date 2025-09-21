package com.lovetropics.perms.mixin;

import com.lovetropics.perms.store.PlayerRoleManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
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
}
