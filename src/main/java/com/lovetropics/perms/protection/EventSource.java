package com.lovetropics.perms.protection;

import net.minecraft.entity.Entity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.function.UnaryOperator;

public final class EventSource {
    private static final EventSource GLOBAL = new EventSource(null, null, null);

    private final RegistryKey<World> dimension;
    private final BlockPos pos;
    private final Entity entity;

    private EventSource(RegistryKey<World> dimension, BlockPos pos, Entity entity) {
        this.dimension = dimension;
        this.pos = pos;
        this.entity = entity;
    }

    public static EventSource global() {
        return GLOBAL;
    }

    public static EventSource at(World world, BlockPos pos) {
        return new EventSource(world.dimension(), pos, null);
    }

    public static EventSource at(RegistryKey<World> dimension, BlockPos pos) {
        return new EventSource(dimension, pos, null);
    }

    public static EventSource allOf(World world) {
        return new EventSource(world.dimension(), null, null);
    }

    public static EventSource allOf(RegistryKey<World> dimension) {
        return new EventSource(dimension, null, null);
    }

    public static EventSource forEntity(Entity entity) {
        return new EventSource(entity.level.dimension(), entity.blockPosition(), entity);
    }

    public static EventSource forEntityAt(Entity entity, BlockPos pos) {
        return new EventSource(entity.level.dimension(), pos, entity);
    }

    public static EventSource transform(EventSource source, UnaryOperator<BlockPos> transform) {
        return new EventSource(source.dimension, source.pos != null ? transform.apply(source.pos) : null, source.entity);
    }

    @Nullable
    public RegistryKey<World> getDimension() {
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
