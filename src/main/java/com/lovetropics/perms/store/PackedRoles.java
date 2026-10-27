package com.lovetropics.perms.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record PackedRoles(List<String> roles) {
    public static final Codec<PackedRoles> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("roles").forGetter(PackedRoles::roles)
    ).apply(i, PackedRoles::new));

    public static final PackedRoles EMPTY = new PackedRoles(List.of());

    public boolean isEmpty() {
        return roles.isEmpty();
    }
}
