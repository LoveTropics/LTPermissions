package com.lovetropics.perms.network;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.client.ClientWarningScreen;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShowWarningMessage(String message) implements CustomPacketPayload {
    public static final Type<ShowWarningMessage> TYPE = new Type<>(LTPermissions.location("show_warning"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ShowWarningMessage> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(ShowWarningMessage::new, ShowWarningMessage::message);

    public static void handle(ShowWarningMessage message, IPayloadContext context) {
        context.enqueueWork(() -> ClientWarningScreen.open(message.message()));
    }

    @Override
    public Type<ShowWarningMessage> type() {
        return TYPE;
    }
}
