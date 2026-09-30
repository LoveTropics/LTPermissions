package com.lovetropics.perms.network;

import com.lovetropics.perms.LTPermissions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.regex.Pattern;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class LTPermissionsNetwork {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(LTPermissions.getCompatVersion());
        registrar.playToClient(ShowWarningMessage.TYPE, ShowWarningMessage.STREAM_CODEC, ShowWarningMessage::handle);
    }

}
