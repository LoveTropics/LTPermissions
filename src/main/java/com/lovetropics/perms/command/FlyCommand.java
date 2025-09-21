package com.lovetropics.perms.command;

import com.lovetropics.perms.LTPermissions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.neoforge.common.NeoForgeMod;

import static net.minecraft.commands.Commands.literal;

public class FlyCommand {
    private static final AttributeModifier FLIGHT_MODIFIER = new AttributeModifier(
            LTPermissions.location("flight"),
            1.0,
            AttributeModifier.Operation.ADD_VALUE
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("fly")
                .requires(ctx -> ctx.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("enable").executes(context -> setFlight(context, true)))
                .then(literal("disable").executes(context -> setFlight(context, false)))
                .executes(FlyCommand::toggleFlight));
    }

    private static int toggleFlight(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        setFlight(player, !player.getAttributes().hasModifier(NeoForgeMod.CREATIVE_FLIGHT, FLIGHT_MODIFIER.id()));
        return Command.SINGLE_SUCCESS;
    }

    private static int setFlight(CommandContext<CommandSourceStack> context, boolean canFly) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        setFlight(player, canFly);
        return Command.SINGLE_SUCCESS;
    }

    private static void setFlight(final ServerPlayer player, final boolean canFly) {
        Abilities abilities = player.getAbilities();
        AttributeInstance instance = player.getAttributes().getInstance(NeoForgeMod.CREATIVE_FLIGHT);
        if (canFly) {
            instance.addPermanentModifier(FLIGHT_MODIFIER);
        } else {
            instance.removeModifier(FLIGHT_MODIFIER);
        }
        abilities.flying &= player.mayFly();
        player.onUpdateAbilities();
    }
}
