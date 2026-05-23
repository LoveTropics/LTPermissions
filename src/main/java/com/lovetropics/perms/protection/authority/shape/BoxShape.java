package com.lovetropics.perms.protection.authority.shape;

import com.lovetropics.lib.BlockBox;
import com.lovetropics.perms.protection.EventSource;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public record BoxShape(ResourceKey<Level> dimension, BlockBox box) implements AuthorityShape {
    public static final MapCodec<BoxShape> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(c -> c.dimension),
            BlockBox.CODEC.fieldOf("box").forGetter(c -> c.box)
    ).apply(i, BoxShape::new));

    @Override
    public boolean accepts(EventSource source) {
        BlockPos pos = source.getPos();
        ResourceKey<Level> dimension = source.getDimension();
        return (dimension == null || dimension == this.dimension) && (pos == null || this.box.contains(pos));
    }

    @Override
    public MapCodec<BoxShape> getCodec() {
        return CODEC;
    }

    public static BoxShape fromRegion(Region region) {
        ResourceKey<Level> dimension = WorldEditShapes.asDimension(region.getWorld());
        BlockPos min = NeoForgeAdapter.get().toBlockPos(region.getMinimumPoint());
        BlockPos max = NeoForgeAdapter.get().toBlockPos(region.getMaximumPoint());
        return new BoxShape(dimension, BlockBox.of(min, max));
    }

    @Nullable
    @Override
    public Region tryIntoRegion(MinecraftServer server) {
        ServerLevel world = server.getLevel(this.dimension);
        return new CuboidRegion(
                NeoForgeAdapter.get().fromNativeWorld(world),
                NeoForgeAdapter.get().adapt(this.box.min()),
                NeoForgeAdapter.get().adapt(this.box.max())
        );
    }
}
