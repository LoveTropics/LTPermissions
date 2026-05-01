package com.lovetropics.perms.mixin.rule;

import com.lovetropics.perms.protection.ProtectionEventDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignBlock.class)
public class SignBlockMixin {
    @Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
    private void openTextEdit(final Player player, final SignBlockEntity sign, final boolean isFrontText, final CallbackInfo ci) {
        if (player instanceof final ServerPlayer serverPlayer) {
            if (ProtectionEventDispatcher.onEditSign(serverPlayer, sign.getBlockPos())) {
                ci.cancel();
            }
        }
    }
}
