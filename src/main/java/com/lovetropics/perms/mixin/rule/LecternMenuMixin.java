package com.lovetropics.perms.mixin.rule;

import com.lovetropics.perms.protection.ProtectionEventDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternMenu.class)
public class LecternMenuMixin {
    @Inject(method = "clickMenuButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Container;removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;"), cancellable = true)
    private void removeBook(final Player player, final int buttonId, final CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof final ServerPlayer serverPlayer && ProtectionEventDispatcher.onRemoveBookFromLectern(serverPlayer)) {
            cir.setReturnValue(false);
        }
    }
}
