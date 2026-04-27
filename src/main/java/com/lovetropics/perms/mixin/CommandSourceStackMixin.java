package com.lovetropics.perms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandSourceStack.class)
public class CommandSourceStackMixin {
    @WrapOperation(
            method = "broadcastToAdmins",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;isOp(Lnet/minecraft/server/players/NameAndId;)Z"
            )
    )
    private boolean shouldReceiveCommandFeedback(PlayerList playerList, NameAndId profile, Operation<Boolean> original) {
        ServerPlayer player = playerList.getPlayer(profile.id());
        RoleReader roles = player != null ? PermissionsApi.lookup().byPlayer(player) : RoleReader.EMPTY;
        Boolean commandFeedback = roles.overrides().getOrNull(LTPermissions.COMMAND_FEEDBACK);
        if (commandFeedback != null) {
            return commandFeedback;
        }
        return original.call(playerList, profile);
    }
}
