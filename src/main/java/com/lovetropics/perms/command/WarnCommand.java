package com.lovetropics.perms.command;

import com.lovetropics.perms.network.ShowWarningMessage;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class WarnCommand {
    private static final String WARN_LITERAL = "warn";
    private static final String TARGET_ARGUMENT = "target";
    private static final String MESSAGE_ARGUMENT = "message";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(WARN_LITERAL)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument(TARGET_ARGUMENT, EntityArgument.player())
                        .then(argument(MESSAGE_ARGUMENT, StringArgumentType.greedyString())
                                .executes(WarnCommand::warn))));
    }

    private static int warn(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, TARGET_ARGUMENT);
        String message = StringArgumentType.getString(ctx, MESSAGE_ARGUMENT);

        PacketDistributor.sendToPlayer(target, new ShowWarningMessage(message));

        ctx.getSource().sendSuccess(() -> Component.literal("Sent warning to " + target.getName().getString()), true);

        return Command.SINGLE_SUCCESS;
    }
}
