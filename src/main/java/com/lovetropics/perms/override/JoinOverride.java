package com.lovetropics.perms.override;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Optional;

public record JoinOverride(boolean allowsJoining, Component notWhitelistedMessage, boolean byPassJoinLimit, Optional<JoinData> joinLimit, Optional<Component> enforce) {

    private static final Component DEFAULT_NOT_WHITELISTED = Component.translatable("multiplayer.disconnect.not_whitelisted");

    public static Codec<JoinOverride> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("allows_joining", false).forGetter(JoinOverride::allowsJoining),
            ComponentSerialization.CODEC.optionalFieldOf("not_whitelisted_message", DEFAULT_NOT_WHITELISTED).forGetter(JoinOverride::notWhitelistedMessage),
            Codec.BOOL.optionalFieldOf("bypass_join_limit", false).forGetter(JoinOverride::byPassJoinLimit),
            JoinData.CODEC.optionalFieldOf("join_limit").forGetter(JoinOverride::joinLimit),
            ComponentSerialization.CODEC.optionalFieldOf("enforce").forGetter(JoinOverride::enforce)
    ).apply(instance, JoinOverride::new));

    public static final JoinOverride DEFAULT = new JoinOverride(false, DEFAULT_NOT_WHITELISTED,false, Optional.empty(), Optional.empty());

    public record JoinData(int limit, Component message) {

        public static final Codec<JoinData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("limit").forGetter(JoinData::limit),
                ComponentSerialization.CODEC.fieldOf("message").forGetter(JoinData::message)
        ).apply(instance, JoinData::new));

    }
}
