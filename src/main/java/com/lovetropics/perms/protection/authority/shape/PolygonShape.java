package com.lovetropics.perms.protection.authority.shape;

import com.lovetropics.lib.BlockBox;
import com.lovetropics.perms.protection.EventSource;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.stream.Collectors;

public final class PolygonShape implements AuthorityShape {
    public static final Codec<PolygonShape> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                World.CODEC.fieldOf("dimension").forGetter(c -> c.dimension),
                BlockPos.CODEC.listOf().fieldOf("points").forGetter(c -> c.points),
                Codec.INT.fieldOf("min_y").forGetter(c -> c.minY),
                Codec.INT.fieldOf("max_y").forGetter(c -> c.maxY)
        ).apply(instance, PolygonShape::new);
    });

    private final RegistryKey<World> dimension;
    private final List<BlockPos> points;
    private final int minY;
    private final int maxY;

    private final BlockBox bounds;

    private final List<BlockVector2> worldEditPoints;

    public PolygonShape(RegistryKey<World> dimension, List<BlockPos> points, int minY, int maxY) {
        this.dimension = dimension;
        this.points = points;
        this.minY = minY;
        this.maxY = maxY;

        BlockPos min = points.get(0);
        BlockPos max = points.get(0);
        for (BlockPos point : points) {
            min = BlockBox.min(min, point);
            max = BlockBox.max(max, point);
        }
        this.bounds = BlockBox.of(
                new BlockPos(min.getX(), minY, min.getZ()),
                new BlockPos(max.getX(), maxY, max.getZ())
        );

        this.worldEditPoints = points.stream()
                .map(pos -> BlockVector2.at(pos.getX(), pos.getZ()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean accepts(EventSource source) {
        RegistryKey<World> dimension = source.getDimension();
        if (!this.acceptsDimension(dimension)) return false;

        BlockPos pos = source.getPos();
        if (pos == null) return true;

        if (this.bounds.contains(pos)) {
            return Polygonal2DRegion.contains(this.worldEditPoints, this.minY, this.maxY, ForgeAdapter.adapt(pos));
        } else {
            return false;
        }
    }

    private boolean acceptsDimension(RegistryKey<World> dimension) {
        return dimension == null || dimension == this.dimension;
    }

    @Override
    public Codec<? extends AuthorityShape> getCodec() {
        return CODEC;
    }

    public static PolygonShape fromRegion(Polygonal2DRegion region) {
        return fromRegion(region, region.getPoints());
    }

    public static PolygonShape fromRegion(Region region, List<BlockVector2> points) {
        RegistryKey<World> dimension = WorldEditShapes.asDimension(region.getWorld());
        List<BlockPos> blockPoints = points.stream()
                .map(pos -> new BlockPos(pos.getX(), 0, pos.getZ()))
                .collect(Collectors.toList());

        int minY = region.getMinimumPoint().getY();
        int maxY = region.getMaximumPoint().getY();

        return new PolygonShape(dimension, blockPoints, minY, maxY);
    }

    @Override
    public Region tryIntoRegion(MinecraftServer server) {
        ServerWorld world = server.getWorld(this.dimension);
        return new Polygonal2DRegion(ForgeAdapter.adapt(world), this.worldEditPoints, this.minY, this.maxY);
    }
}
