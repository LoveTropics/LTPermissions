package com.lovetropics.perms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandSourceStack.class)
public class CommandSourceStackMixin {
    @WrapOperation(
            method = "broadcastToAdmins",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;isOp(Lcom/mojang/authlib/GameProfile;)Z"
            )
    )
    private boolean shouldReceiveCommandFeedback(PlayerList playerList, GameProfile profile, Operation<Boolean> original) {
        ServerPlayer player = playerList.getPlayer(profile.getId());
        RoleReader roles = player != null ? PermissionsApi.lookup().byPlayer(player) : RoleReader.EMPTY;
        Boolean commandFeedback = roles.overrides().getOrNull(LTPermissions.COMMAND_FEEDBACK);
        if (commandFeedback != null) {
            return commandFeedback;
        }
        return original.call(playerList, profile);
    }
}
