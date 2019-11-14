package com.lovetropics.perms;

import com.google.common.base.Preconditions;
import com.lovetropics.perms.capability.DelegatedCapStorage;
import com.lovetropics.perms.capability.PlayerRoles;
import com.lovetropics.perms.command.RoleCommand;
import com.lovetropics.perms.modifier.ChatStyleModifier;
import com.lovetropics.perms.modifier.RoleModifierType;
import com.lovetropics.perms.modifier.command.CommandPermEvaluator;
import com.lovetropics.perms.modifier.command.CommandRequirementHooks;
import com.lovetropics.perms.modifier.command.PermissionResult;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(LTPerms.ID)
public class LTPerms {
    public static final String ID = "ltperms";

    public static final Logger LOGGER = LogManager.getLogger(ID);

    @CapabilityInject(PlayerRoles.class)
    private static Capability<PlayerRoles> playerRoleCap;

    public LTPerms() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        MinecraftForge.EVENT_BUS.addListener(this::serverStarting);
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onChat);

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, this::attachEntityCapabilities);
    }

    private void setup(FMLCommonSetupEvent event) {
        RoleConfiguration.setup();

        CapabilityManager.INSTANCE.register(PlayerRoles.class, new DelegatedCapStorage<>(), () -> {
            throw new UnsupportedOperationException();
        });
    }

    private void serverStarting(FMLServerStartingEvent event) {
        RoleCommand.register(event.getCommandDispatcher());
    }

    private void serverStarted(FMLServerStartedEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getServer().getCommandManager().getDispatcher();

        try {
            CommandRequirementHooks<CommandSource> hooks = CommandRequirementHooks.tryCreate((node, predicate) -> {
                return source -> {
                    PermissionResult result = CommandPermEvaluator.canUseCommand(source, node);
                    if (result == PermissionResult.ALLOW) return true;
                    if (result == PermissionResult.DENY) return false;

                    return predicate.test(source);
                };
            });

            hooks.hookAll(dispatcher);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to hook command requirements", e);
        }
    }

    private void attachEntityCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();
        if (entity.world.isRemote) return;

        if (entity instanceof ServerPlayerEntity) {
            event.addCapability(new ResourceLocation(LTPerms.ID, "roles"), new PlayerRoles((ServerPlayerEntity) entity));
        }
    }

    private void onChat(ServerChatEvent event) {
        ServerPlayerEntity player = event.getPlayer();

        player.getCapability(playerRolesCap()).ifPresent(roles -> {
            ChatStyleModifier chatStyle = roles.getHighest(RoleModifierType.CHAT_STYLE);
            if (chatStyle != null) {
                event.setComponent(chatStyle.make(player.getDisplayName(), event.getMessage()));
            }
        });
    }

    public static Capability<PlayerRoles> playerRolesCap() {
        Preconditions.checkNotNull(playerRoleCap, "player role capability not initialized");
        return playerRoleCap;
    }
}
