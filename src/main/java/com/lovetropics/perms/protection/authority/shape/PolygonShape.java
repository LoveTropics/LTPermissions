package com.lovetropics.perms.protection.authority.shape;

import com.lovetropics.lib.BlockBox;
import com.lovetropics.perms.protection.EventSource;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.stream.Collectors;

public final class PolygonShape implements AuthorityShape {
    public static final MapCodec<PolygonShape> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(c -> c.dimension),
            BlockPos.CODEC.listOf().fieldOf("points").forGetter(c -> c.points),
            Codec.INT.fieldOf("min_y").forGetter(c -> c.minY),
            Codec.INT.fieldOf("max_y").forGetter(c -> c.maxY)
    ).apply(i, PolygonShape::new));

    private final ResourceKey<Level> dimension;
    private final List<BlockPos> points;
    private final int minY;
    private final int maxY;

    private final BlockBox bounds;

    private final List<BlockVector2> worldEditPoints;

    public PolygonShape(ResourceKey<Level> dimension, List<BlockPos> points, int minY, int maxY) {
        this.dimension = dimension;
        this.points = points;
        this.minY = minY;
        this.maxY = maxY;

        BlockPos min = points.getFirst();
        BlockPos max = points.getFirst();
        for (BlockPos point : points) {
            min = BlockPos.min(min, point);
            max = BlockPos.max(max, point);
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
        ResourceKey<Level> dimension = source.getDimension();
        if (!this.acceptsDimension(dimension)) {
            return false;
        }

        BlockPos pos = source.getPos();
        if (pos == null) {
            return true;
        }

        if (this.bounds.contains(pos)) {
            return Polygonal2DRegion.contains(this.worldEditPoints, this.minY, this.maxY, NeoForgeAdapter.adapt(pos));
        } else {
            return false;
        }
    }

    private boolean acceptsDimension(ResourceKey<Level> dimension) {
        return dimension == null || dimension == this.dimension;
    }

    @Override
    public MapCodec<PolygonShape> getCodec() {
        return CODEC;
    }

    public static PolygonShape fromRegion(Polygonal2DRegion region) {
        return fromRegion(region, region.getPoints());
    }

    public static PolygonShape fromRegion(Region region, List<BlockVector2> points) {
        ResourceKey<Level> dimension = WorldEditShapes.asDimension(region.getWorld());
        List<BlockPos> blockPoints = points.stream()
                .map(pos -> new BlockPos(pos.x(), 0, pos.z()))
                .toList();

        int minY = region.getMinimumPoint().y();
        int maxY = region.getMaximumPoint().y();

        return new PolygonShape(dimension, blockPoints, minY, maxY);
    }

    @Override
    public Region tryIntoRegion(MinecraftServer server) {
        ServerLevel world = server.getLevel(this.dimension);
        return new Polygonal2DRegion(NeoForgeAdapter.adapt(world), this.worldEditPoints, this.minY, this.maxY);
    }
}
