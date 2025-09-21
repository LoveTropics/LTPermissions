package com.lovetropics.perms.protection.authority.shape;

import com.lovetropics.perms.protection.EventSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record DimensionShape(ResourceKey<Level> dimension) implements AuthorityShape {
    public static final MapCodec<DimensionShape> CODEC = Level.RESOURCE_KEY_CODEC.xmap(DimensionShape::new, shape -> shape.dimension).fieldOf("value");

    @Override
    public boolean accepts(EventSource source) {
        ResourceKey<Level> dimension = source.getDimension();
        return dimension == null || dimension == this.dimension;
    }

    @Override
    public MapCodec<DimensionShape> getCodec() {
        return CODEC;
    }
}
