package com.lovetropics.perms.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.function.UnaryOperator;

public final class EventSource {
    private static final EventSource GLOBAL = new EventSource(null, null, null);

    private final ResourceKey<Level> dimension;
    @Nullable
    private final BlockPos pos;
    @Nullable
    private final Entity entity;

    private EventSource(ResourceKey<Level> dimension, BlockPos pos, Entity entity) {
        this.dimension = dimension;
        this.pos = pos;
        this.entity = entity;
    }

    public static EventSource global() {
        return GLOBAL;
    }

    public static EventSource at(Level world, BlockPos pos) {
        return new EventSource(world.dimension(), pos, null);
    }

    public static EventSource at(ResourceKey<Level> dimension, BlockPos pos) {
        return new EventSource(dimension, pos, null);
    }

    public static EventSource allOf(Level world) {
        return new EventSource(world.dimension(), null, null);
    }

    public static EventSource allOf(ResourceKey<Level> dimension) {
        return new EventSource(dimension, null, null);
    }

    public static EventSource forEntity(Entity entity) {
        return new EventSource(entity.level().dimension(), entity.blockPosition(), entity);
    }

    public static EventSource forEntityAt(Entity entity, BlockPos pos) {
        return new EventSource(entity.level().dimension(), pos, entity);
    }

    public static EventSource forOptionalEntityAt(Level level, @Nullable Entity entity, BlockPos pos) {
        return new EventSource(level.dimension(), pos, entity);
    }

    public static EventSource transform(EventSource source, UnaryOperator<BlockPos> transform) {
        return new EventSource(source.dimension, source.pos != null ? transform.apply(source.pos) : null, source.entity);
    }

    @Nullable
    public ResourceKey<Level> getDimension() {
        return this.dimension;
    }

    @Nullable
    public BlockPos getPos() {
        return this.pos;
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }
}
