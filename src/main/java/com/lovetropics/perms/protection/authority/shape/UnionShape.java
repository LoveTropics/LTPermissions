package com.lovetropics.perms.protection.authority.shape;

import com.lovetropics.lib.codec.MoreCodecs;
import com.lovetropics.perms.protection.EventSource;
import com.mojang.serialization.MapCodec;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionIntersection;
import com.sk89q.worldedit.world.World;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record UnionShape(AuthorityShape... shapes) implements AuthorityShape {
    public static final MapCodec<UnionShape> CODEC = MoreCodecs.arrayOrUnit(AuthorityShape.CODEC, AuthorityShape[]::new)
            .xmap(UnionShape::new, shape -> shape.shapes)
            .fieldOf("value");

    @Override
    public boolean accepts(EventSource source) {
        for (AuthorityShape shape : this.shapes) {
            if (shape.accepts(source)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MapCodec<UnionShape> getCodec() {
        return CODEC;
    }

    public static UnionShape fromRegion(RegionIntersection region) {
        AuthorityShape[] shapes = IntersectionRegionsGetter.apply(region).stream()
                .map(WorldEditShapes::tryFromRegion)
                .filter(Objects::nonNull)
                .toArray(AuthorityShape[]::new);
        return new UnionShape(shapes);
    }

    @Nullable
    @Override
    public Region tryIntoRegion(MinecraftServer server) {
        List<Region> regions = Arrays.stream(this.shapes)
                .map(shape -> shape.tryIntoRegion(server))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!regions.isEmpty()) {
            World world = regions.get(0).getWorld();
            return new RegionIntersection(world, regions);
        } else {
            return null;
        }
    }

    static final class IntersectionRegionsGetter {
        private static final MethodHandle GET_REGIONS;

        static {
            try {
                Field field = RegionIntersection.class.getDeclaredField("regions");
                field.setAccessible(true);
                GET_REGIONS = MethodHandles.lookup().unreflectGetter(field);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to reflect RegionIntersection.regions", e);
            }
        }

        @SuppressWarnings("unchecked")
        static List<Region> apply(RegionIntersection region) {
            try {
                return (List<Region>) GET_REGIONS.invokeExact(region);
            } catch (Throwable e) {
                throw new RuntimeException("Unable to get RegionIntersection.regions", e);
            }
        }
    }
}
