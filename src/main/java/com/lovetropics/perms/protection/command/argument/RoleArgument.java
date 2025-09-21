package com.lovetropics.perms.protection.command.argument;

import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.Role;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

public final class RoleArgument {
    private static final DynamicCommandExceptionType ROLE_DOES_NOT_EXIST = new DynamicCommandExceptionType(arg ->
            Component.translatable("Role with id '%s' does not exist!", arg)
    );

    public static RequiredArgumentBuilder<CommandSourceStack, String> argument(String name) {
        return Commands.argument(name, StringArgumentType.string())
                .suggests((context, builder) -> {
                    return SharedSuggestionProvider.suggest(
                            PermissionsApi.provider().stream().map(Role::id),
                            builder
                    );
                });
    }

    @Nonnull
    public static Role get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, name);
        Role role = PermissionsApi.provider().get(id);
        if (role == null) {
            throw ROLE_DOES_NOT_EXIST.create(id);
        }

        return role;
    }
}
