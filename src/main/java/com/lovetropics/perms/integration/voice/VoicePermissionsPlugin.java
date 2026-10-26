package com.lovetropics.perms.integration.voice;

import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

@ForgeVoicechatPlugin
public class VoicePermissionsPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return LTPermissions.ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null || !shouldBroadcastVoiceFor(senderConnection)) {
            return;
        }

        event.cancel();

        VoicechatServerApi api = event.getVoicechat();

        StaticSoundPacket staticPacket = event.getPacket().staticSoundPacketBuilder().build();

        ServerLevel level = (ServerLevel) senderConnection.getPlayer().getServerLevel().getServerLevel();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.getUUID().equals(senderConnection.getPlayer().getUuid())) {
                continue;
            }
            VoicechatConnection receiverConnection = api.getConnectionOf(player.getUUID());
            if (receiverConnection != null) {
                api.sendStaticSoundPacketTo(receiverConnection, staticPacket);
            }
        }
    }

    private boolean shouldBroadcastVoiceFor(VoicechatConnection sender) {
        RoleReader roles = PermissionsApi.lookup().byPlayer((ServerPlayer) sender.getPlayer().getPlayer());
        return roles.overrides().get(LTPermissions.BROADCAST_VOICE, false);
    }
}
