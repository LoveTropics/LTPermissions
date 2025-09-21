package com.lovetropics.perms.override.command;

import com.lovetropics.lib.permission.PermissionResult;
import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class CommandOverride {
    public static final Codec<CommandOverride> CODEC = CommandOverrideRules.CODEC.xmap(CommandOverride::new, CommandOverride::rules);

    private final CommandOverrideRules rules;

    CommandOverride(CommandOverrideRules rules) {
        this.rules = rules;
    }

    public CommandOverrideRules rules() {
        return this.rules;
    }

    public static CommandOverride build(List<CommandOverride> overrides) {
        List<CommandOverrideRules> rules = overrides.stream().map(CommandOverride::rules).toList();
        return new CommandOverride(CommandOverrideRules.combine(rules));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        hookCommands(event.getDispatcher());
    }

    private static void hookCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            CommandRequirementHooks<CommandSourceStack> hooks = CommandRequirementHooks.tryCreate((nodes, parent) -> {
                MatchableCommand command = MatchableCommand.compile(nodes);

                return source -> switch (canUseCommand(source, command)) {
                    case ALLOW -> true;
                    case DENY -> false;
                    default -> parent.test(source);
                };
            });

            hooks.applyTo(dispatcher);
        } catch (ReflectiveOperationException e) {
            LTPermissions.LOGGER.error("Failed to reflect into command requirements!", e);
        }
    }

    private static PermissionResult canUseCommand(CommandSourceStack source, MatchableCommand command) {
        if (doesBypassPermissions(source)) {
            return PermissionResult.PASS;
        }

        RoleReader roles = PermissionsApi.lookup().bySource(source);
        return roles.overrides().test(LTPermissions.COMMANDS, m -> m.test(command));
    }

    public static boolean doesBypassPermissions(CommandSourceStack source) {
        return source.hasPermission(4);
    }

    public PermissionResult test(MatchableCommand command) {
        return this.rules.test(command);
    }
}
