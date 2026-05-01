package com.lovetropics.perms.mixin.rule;

import com.lovetropics.perms.protection.ProtectionEventDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {
    @Inject(at = @At("HEAD"), method = "slotChangedCraftingGrid", cancellable = true)
    private static void respectCraftingPermission(
            AbstractContainerMenu menu,
            ServerLevel level,
            Player player,
            CraftingContainer container,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> recipeHint,
            CallbackInfo ci
    ) {
        if (player instanceof ServerPlayer sp && ProtectionEventDispatcher.onCraft(sp)) {
            ci.cancel();
        }
    }
}
