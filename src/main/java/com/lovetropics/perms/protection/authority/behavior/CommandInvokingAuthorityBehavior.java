package com.lovetropics.perms.protection.authority.behavior;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

public final class CommandInvokingAuthorityBehavior implements AuthorityBehavior {
    private final String[] enter;
    private final String[] exit;
    private final boolean commandFeedback;

    public CommandInvokingAuthorityBehavior(String[] enter, String[] exit, boolean commandFeedback) {
        this.enter = enter;
        this.exit = exit;
        this.commandFeedback = commandFeedback;
    }

    @Override
    public void onPlayerEnter(ServerPlayer player) {
        this.invokeCommands(player, this.enter);
    }

    @Override
    public void onPlayerExit(ServerPlayer player) {
        this.invokeCommands(player, this.exit);
    }

    private void invokeCommands(ServerPlayer player, String[] commands) {
        if (commands.length == 0) {
            return;
        }

        CommandSourceStack source = this.getSource(player);
        Commands commandManager = player.level().getServer().getCommands();
        for (String command : commands) {
            commandManager.performPrefixedCommand(source, command);
        }
    }

    private CommandSourceStack getSource(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        if (!this.commandFeedback) {
            source = source.withSuppressedOutput();
        }
        return source;
    }
}
