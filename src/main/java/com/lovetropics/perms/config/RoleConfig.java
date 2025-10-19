package com.lovetropics.perms.config;

import com.lovetropics.lib.permission.role.Role;
import com.lovetropics.perms.override.RoleOverrideMap;
import com.lovetropics.perms.role.SimpleRole;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.List;

public record RoleConfig(RoleOverrideMap overrides, List<String> includes) {
    public static final Codec<RoleConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            RoleOverrideMap.CODEC.optionalFieldOf("overrides", RoleOverrideMap.EMPTY).forGetter(RoleConfig::overrides),
            ExtraCodecs.compactListCodec(Codec.STRING).optionalFieldOf("includes", List.of()).forGetter(RoleConfig::includes)
    ).apply(i, RoleConfig::new));

    public Role create(String name, int index) {
        return new SimpleRole(name, overrides, index);
    }
}
