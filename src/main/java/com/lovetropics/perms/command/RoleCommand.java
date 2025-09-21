package com.lovetropics.perms.command;

import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.override.command.CommandOverride;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.lovetropics.perms.store.PlayerRoleSet;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RoleCommand {
    public static final DynamicCommandExceptionType ROLE_NOT_FOUND = new DynamicCommandExceptionType(arg -> Component.translatable("Role with name '%s' was not found!", arg));

    public static final SimpleCommandExceptionType ROLE_POWER_TOO_LOW = new SimpleCommandExceptionType(Component.literal("You do not have sufficient power to manage this role"));

    public static final SimpleCommandExceptionType TOO_MANY_SELECTED = new SimpleCommandExceptionType(Component.literal("Too many players selected!"));

    // @formatter:off
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("role")
                .requires(s -> s.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("assign")
                    .then(argument("targets", GameProfileArgument.gameProfile())
                    .then(argument("role", StringArgumentType.word()).suggests(roleSuggestions())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
                        String roleName = StringArgumentType.getString(ctx, "role");
                        return updateRoles(source, targets, roleName, PlayerRoleSet::add, "'%s' assigned to %s players");
                    })
                )))
                .then(literal("remove")
                    .then(argument("targets", GameProfileArgument.gameProfile())
                    .then(argument("role", StringArgumentType.word()).suggests(roleSuggestions())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
                        String roleName = StringArgumentType.getString(ctx, "role");
                        return updateRoles(source, targets, roleName, PlayerRoleSet::remove, "'%s' removed from %s players");
                    })
                )))
                .then(literal("list")
                    .then(argument("target", GameProfileArgument.gameProfile()).executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        Collection<GameProfile> gameProfiles = GameProfileArgument.getGameProfiles(ctx, "target");
                        if (gameProfiles.size() != 1) {
                            throw TOO_MANY_SELECTED.create();
                        }
                        return listRoles(source, gameProfiles.iterator().next());
                    }))
                )
                .then(literal("reload").executes(ctx -> reloadRoles(ctx.getSource())))
        );
    }
    // @formatter:on

    private static int updateRoles(CommandSourceStack source, Collection<GameProfile> players, String roleName, BiPredicate<PlayerRoleSet, Role> apply, String success) throws CommandSyntaxException {
        Role role = getAssignableRole(roleName);
        requireHasPower(source, role);

        PlayerRoleManager roleManager = PlayerRoleManager.get();

        int count = 0;
        for (GameProfile player : players) {
            boolean applied = roleManager.updateRoles(player.getId(), roles -> apply.test(roles, role));
            if (applied) {
                count++;
            }
        }

        int finalCount = count;
        source.sendSuccess(() -> Component.translatable(success, roleName, finalCount), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int listRoles(CommandSourceStack source, GameProfile player) {
        PlayerRoleManager roleManager = PlayerRoleManager.get();

        List<Role> roles = roleManager.peekRoles(player.getId())
                .stream().collect(Collectors.toList());
        source.sendSuccess(() -> {
            Component rolesComponent = ComponentUtils.formatList(roles, role -> Component.literal(role.id()).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
            return Component.translatable("Found %s roles on player: %s", roles.size(), rolesComponent);
        }, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int reloadRoles(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<String> errors = RolesConfig.reload(server.getResourceManager());

        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Role configuration successfully reloaded"), false);
        } else {
            MutableComponent errorFeedback = Component.literal("Failed to reload roles configuration!");
            for (String error : errors) {
                errorFeedback = errorFeedback.append("\n - " + error);
            }
            source.sendFailure(errorFeedback);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static void requireHasPower(CommandSourceStack source, Role role) throws CommandSyntaxException {
        if (hasAdminPower(source)) {
            return;
        }

        Role highestRole = getHighestRole(source);
        if (highestRole == null || role.compareTo(highestRole) <= 0) {
            throw ROLE_POWER_TOO_LOW.create();
        }
    }

    private static Role getAssignableRole(String roleName) throws CommandSyntaxException {
        Role role = RolesConfig.get().get(roleName);
        if (role == null || roleName.equals(Role.EVERYONE)) {
            throw ROLE_NOT_FOUND.create(roleName);
        }
        return role;
    }

    private static SuggestionProvider<CommandSourceStack> roleSuggestions() {
        return (ctx, builder) -> {
            CommandSourceStack source = ctx.getSource();

            boolean admin = hasAdminPower(source);
            Role highestRole = getHighestRole(source);
            Comparator<Role> comparator = Comparator.nullsLast(Comparator.naturalOrder());

            return SharedSuggestionProvider.suggest(
                    RolesConfig.get().stream()
                            .filter(role -> !role.id().equals(Role.EVERYONE))
                            .filter(role -> admin || comparator.compare(role, highestRole) < 0)
                            .map(Role::id),
                    builder
            );
        };
    }

    @Nullable
    private static Role getHighestRole(CommandSourceStack source) {
        return PermissionsApi.lookup().bySource(source).stream()
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean hasAdminPower(CommandSourceStack source) {
        return source.getEntity() == null || CommandOverride.doesBypassPermissions(source);
    }
}
