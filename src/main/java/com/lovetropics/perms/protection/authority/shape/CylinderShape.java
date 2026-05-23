package com.lovetropics.perms.protection.authority.shape;

import com.lovetropics.lib.BlockBox;
import com.lovetropics.perms.protection.EventSource;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class CylinderShape implements AuthorityShape {
    public static final MapCodec<CylinderShape> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(c -> c.dimension),
            Codec.INT.fieldOf("center_x").forGetter(c -> c.centerX),
            Codec.INT.fieldOf("center_z").forGetter(c -> c.centerZ),
            Codec.INT.fieldOf("min_y").forGetter(c -> c.minY),
            Codec.INT.fieldOf("max_y").forGetter(c -> c.maxY),
            Codec.INT.fieldOf("radius_x").forGetter(c -> c.radiusX),
            Codec.INT.fieldOf("radius_z").forGetter(c -> c.radiusZ)
    ).apply(i, CylinderShape::new));

    private final ResourceKey<Level> dimension;
    private final int centerX;
    private final int centerZ;
    private final int minY;
    private final int maxY;
    private final int radiusX;
    private final int radiusZ;

    private final BlockBox bounds;

    public CylinderShape(ResourceKey<Level> dimension, int centerX, int centerZ, int minY, int maxY, int radiusX, int radiusZ) {
        this.dimension = dimension;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.minY = minY;
        this.maxY = maxY;
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;

        this.bounds = BlockBox.of(
                new BlockPos(centerX - radiusX, minY, centerZ - radiusZ),
                new BlockPos(centerX + radiusX, maxY, centerZ + radiusZ)
        );
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
            float dx = (float) (pos.getX() - this.centerX) / this.radiusX;
            float dz = (float) (pos.getZ() - this.centerZ) / this.radiusZ;
            return dx * dx + dz * dz <= 1.0F;
        } else {
            return false;
        }
    }

    private boolean acceptsDimension(ResourceKey<Level> dimension) {
        return dimension == null || dimension == this.dimension;
    }

    @Override
    public MapCodec<CylinderShape> getCodec() {
        return CODEC;
    }

    public static CylinderShape fromRegion(CylinderRegion region) {
        ResourceKey<Level> dimension = WorldEditShapes.asDimension(region.getWorld());
        Vector3 center = region.getCenter();
        Vector2 radius = region.getRadius();
        int minY = region.getMinimumY();
        int maxY = region.getMaximumY();

        return new CylinderShape(
                dimension,
                center.blockX(), center.blockZ(),
                minY, maxY,
                radius.blockX(), radius.blockZ()
        );
    }

    @Override
    public Region tryIntoRegion(MinecraftServer server) {
        ServerLevel world = server.getLevel(this.dimension);
        return new CylinderRegion(
                NeoForgeAdapter.get().fromNativeWorld(world),
                BlockVector3.at(this.centerX, 0, this.centerZ),
                Vector2.at(this.radiusX, this.radiusZ),
                this.minY, this.maxY
        );
    }
}
