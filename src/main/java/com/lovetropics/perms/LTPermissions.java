package com.lovetropics.perms;

import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.lib.permission.role.RoleLookup;
import com.lovetropics.lib.permission.role.RoleOverrideType;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.command.FlyCommand;
import com.lovetropics.perms.command.RoleCommand;
import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.override.NameDecorationOverride;
import com.lovetropics.perms.override.command.CommandOverride;
import com.lovetropics.perms.protection.authority.shape.AuthorityShape;
import com.lovetropics.perms.protection.command.ProtectCommand;
import com.lovetropics.perms.store.PlayerRoleManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.waypoints.Waypoint;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Map;

@Mod(LTPermissions.ID)
public class LTPermissions {
    public static final String ID = "ltpermissions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final RoleOverrideType<CommandOverride> COMMANDS = RoleOverrideType.register("commands", CommandOverride.CODEC)
            .withBuilder(CommandOverride::build)
            .withChangeListener(player -> {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    server.getCommands().sendCommands(player);
                }
            });

    public static final RoleOverrideType<NameDecorationOverride> NAME_DECORATION = RoleOverrideType.register("name_decoration", NameDecorationOverride.CODEC)
            .withBuilder(NameDecorationOverride::build)
            .withInitializeListener(LTPermissions::refreshNameDecoration)
            .withChangeListener(LTPermissions::refreshNameDecoration);

    private static void refreshNameDecoration(final ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    public static final RoleOverrideType<Boolean> MUTE = RoleOverrideType.register("mute", Codec.BOOL);
    public static final RoleOverrideType<Boolean> COMMAND_FEEDBACK = RoleOverrideType.register("command_feedback", Codec.BOOL);
    public static final RoleOverrideType<Boolean> BYPASS_WHITELIST = RoleOverrideType.register("bypass_whitelist", Codec.BOOL);

    public static final RoleOverrideType<Waypoint.Icon> WAYPOINT_ICON = RoleOverrideType.register("waypoint_icon", Waypoint.Icon.CODEC)
            .withChangeListener(player -> {
                ServerWaypointManager waypointManager = player.level().getWaypointManager();
                waypointManager.untrackWaypoint(player);
                waypointManager.trackWaypoint(player);
            });

    private static final RoleLookup LOOKUP = new RoleLookup() {
        @Override
        @Nonnull
        public RoleReader byEntity(Entity entity) {
            if (entity instanceof ServerPlayer) {
                RoleReader roles = PlayerRoleManager.get().getRolesForOnline(((ServerPlayer) entity));
                return roles != null ? roles : RoleReader.EMPTY;
            }
            return RoleReader.EMPTY;
        }

        @Override
        @Nonnull
        public RoleReader bySource(CommandSourceStack source) {
            Entity entity = source.getEntity();
            return entity != null ? this.byEntity(entity) : RoleReader.EMPTY;
        }
    };

    public LTPermissions(IEventBus modBus) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerChat);

        PermissionsApi.setRoleLookup(LOOKUP);

        EntitySelectorOptions.register("role", parser -> {
            boolean inverted = parser.shouldInvertValue();
            parser.setSuggestions((builder, consumer) -> SharedSuggestionProvider.suggest(RolesConfig.get().stream().map(Role::id), builder));
            String name = parser.getReader().readUnquotedString();
            parser.addPredicate(entity -> {
                final RoleReader roles = PermissionsApi.lookup().byEntity(entity);
                for (final Role role : roles) {
                    if (name.equals(role.id())) {
                        return !inverted;
                    }
                }
                return inverted;
            });
        }, parser -> true, Component.literal("Player Role"));
    }

    private void setup(FMLCommonSetupEvent event) {
        AuthorityShape.register();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        RoleCommand.register(dispatcher);
        FlyCommand.register(dispatcher);
        ProtectCommand.register(dispatcher);

        CommandAliasConfiguration aliasConfig = CommandAliasConfiguration.load();
        for (Map.Entry<String, String[]> entry : aliasConfig.aliases().entrySet()) {
            String[] literals = entry.getKey().split(" ");

            LiteralArgumentBuilder<CommandSourceStack>[] nodes = new LiteralArgumentBuilder[literals.length];
            for (int i = 0; i < literals.length; i++) {
                nodes[i] = Commands.literal(literals[i]);
            }

            String[] commands = entry.getValue();
            LiteralArgumentBuilder<CommandSourceStack> last = nodes[nodes.length - 1];
            last.executes(context -> {
                CommandSourceStack source = context.getSource().withPermission(4).withSuppressedOutput();
                for (String command : commands) {
                    source.getServer().getCommands().performPrefixedCommand(source, command);
                }
                return Command.SINGLE_SUCCESS;
            });

            for (int i = nodes.length - 2; i >= 0; i--) {
                last = nodes[i].then(last);
            }

            dispatcher.register(last);
        }
    }

    private void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();

        RoleReader roles = PermissionsApi.lookup().byPlayer(player);
        if (roles.overrides().test(MUTE)) {
            player.displayClientMessage(Component.literal("You are muted!").withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
        }
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
