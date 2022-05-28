package com.lovetropics.perms.command;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.override.command.CommandOverride;
import com.lovetropics.perms.role.Role;
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
import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;

public final class RoleCommand {
    public static final DynamicCommandExceptionType ROLE_NOT_FOUND = new DynamicCommandExceptionType(arg -> {
        return new TranslationTextComponent("Role with name '%s' was not found!", arg);
    });

    public static final SimpleCommandExceptionType ROLE_POWER_TOO_LOW = new SimpleCommandExceptionType(
            new StringTextComponent("You do not have sufficient power to manage this role")
    );

    public static final SimpleCommandExceptionType TOO_MANY_SELECTED = new SimpleCommandExceptionType(
            new StringTextComponent("Too many players selected!")
    );

    // @formatter:off
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(literal("role")
                .requires(s -> s.hasPermission(4))
                .then(literal("assign")
                    .then(argument("targets", GameProfileArgument.gameProfile())
                    .then(argument("role", StringArgumentType.word()).suggests(roleSuggestions())
                    .executes(ctx -> {
                        CommandSource source = ctx.getSource();
                        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
                        String roleName = StringArgumentType.getString(ctx, "role");
                        return updateRoles(source, targets, roleName, PlayerRoleSet::add, "'%s' assigned to %s players");
                    })
                )))
                .then(literal("remove")
                    .then(argument("targets", GameProfileArgument.gameProfile())
                    .then(argument("role", StringArgumentType.word()).suggests(roleSuggestions())
                    .executes(ctx -> {
                        CommandSource source = ctx.getSource();
                        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
                        String roleName = StringArgumentType.getString(ctx, "role");
                        return updateRoles(source, targets, roleName, PlayerRoleSet::remove, "'%s' removed from %s players");
                    })
                )))
                .then(literal("list")
                    .then(argument("target", GameProfileArgument.gameProfile()).executes(ctx -> {
                        CommandSource source = ctx.getSource();
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

    private static int updateRoles(CommandSource source, Collection<GameProfile> players, String roleName, BiPredicate<PlayerRoleSet, Role> apply, String success) throws CommandSyntaxException {
        Role role = getRole(roleName);
        requireHasPower(source, role);

        PlayerRoleManager roleManager = PlayerRoleManager.get();

        int count = 0;
        for (GameProfile player : players) {
            boolean applied = roleManager.updateRoles(player.getId(), roles -> apply.test(roles, role));
            if (applied) {
                count++;
            }
        }

        source.sendSuccess(new TranslationTextComponent(success, roleName, count), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int listRoles(CommandSource source, GameProfile player) {
        PlayerRoleManager roleManager = PlayerRoleManager.get();

        List<Role> roles = roleManager.peekRoles(player.getId())
                .stream().collect(Collectors.toList());
        ITextComponent rolesComponent = TextComponentUtils.formatList(roles, role -> new StringTextComponent(role.id()).setStyle(Style.EMPTY.withColor(TextFormatting.GRAY)));
        source.sendSuccess(new TranslationTextComponent("Found %s roles on player: %s", roles.size(), rolesComponent), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int reloadRoles(CommandSource source) {
        MinecraftServer server = source.getServer();

        server.execute(() -> {
            List<String> errors = RolesConfig.setup();

            PlayerRoleManager roleManager = PlayerRoleManager.get();
            roleManager.onRoleReload(server, RolesConfig.get());

            if (errors.isEmpty()) {
                source.sendSuccess(new StringTextComponent("Role configuration successfully reloaded"), false);
            } else {
                IFormattableTextComponent errorFeedback = new StringTextComponent("Failed to reload roles configuration!");
                for (String error : errors) {
                    errorFeedback = errorFeedback.append("\n - " + error);
                }
                source.sendFailure(errorFeedback);
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private static void requireHasPower(CommandSource source, Role role) throws CommandSyntaxException {
        if (hasAdminPower(source)) {
            return;
        }

        Role highestRole = getHighestRole(source);
        if (highestRole == null || role.compareTo(highestRole) <= 0) {
            throw ROLE_POWER_TOO_LOW.create();
        }
    }

    private static Role getRole(String roleName) throws CommandSyntaxException {
        Role role = RolesConfig.get().get(roleName);
        if (role == null) throw ROLE_NOT_FOUND.create(roleName);
        return role;
    }

    private static SuggestionProvider<CommandSource> roleSuggestions() {
        return (ctx, builder) -> {
            CommandSource source = ctx.getSource();

            boolean admin = hasAdminPower(source);
            Role highestRole = getHighestRole(source);
            Comparator<Role> comparator = Comparator.<Role>nullsLast(Comparator.<Role>naturalOrder());

            return ISuggestionProvider.suggest(
                    RolesConfig.get().stream()
                            .filter(role -> admin || comparator.compare(role, highestRole) < 0)
                            .map(Role::id),
                    builder
            );
        };
    }

    @Nullable
    private static Role getHighestRole(CommandSource source) {
        return LTPermissions.lookup().bySource(source).stream()
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean hasAdminPower(CommandSource source) {
        return source.getEntity() == null || CommandOverride.doesBypassPermissions(source);
    }
}
