package com.lovetropics.perms.protection.authority.behavior.config;

import com.lovetropics.perms.protection.ProtectionRuleMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.ExtraCodecs;

import java.util.List;

public record AuthorityBehavior(
        List<String> enterCommands,
        List<String> exitCommands,
        boolean commandFeedback,
        ProtectionRuleMap rules
) {
    public static final Codec<AuthorityBehavior> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.compactListCodec(Codec.STRING).fieldOf("enter_commands").forGetter(AuthorityBehavior::enterCommands),
            ExtraCodecs.compactListCodec(Codec.STRING).fieldOf("exit_commands").forGetter(AuthorityBehavior::exitCommands),
            Codec.BOOL.optionalFieldOf("command_feedback", false).forGetter(AuthorityBehavior::commandFeedback),
            ProtectionRuleMap.CODEC.optionalFieldOf("rules", ProtectionRuleMap.EMPTY).forGetter(AuthorityBehavior::rules)
    ).apply(i, AuthorityBehavior::new));

    public void onPlayerEnter(ServerPlayer player) {
        invokeCommands(player, enterCommands, commandFeedback);
    }

    public void onPlayerExit(ServerPlayer player) {
        invokeCommands(player, exitCommands, commandFeedback);
    }

    private static void invokeCommands(ServerPlayer player, List<String> commands, boolean commandFeedback) {
        if (commands.isEmpty()) {
            return;
        }
        CommandSourceStack source = getSource(player, commandFeedback);
        Commands commandManager = player.level().getServer().getCommands();
        for (String command : commands) {
            commandManager.performPrefixedCommand(source, command);
        }
    }

    private static CommandSourceStack getSource(ServerPlayer player, boolean commandFeedback) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        if (!commandFeedback) {
            source = source.withSuppressedOutput();
        }
        return source;
    }
}
