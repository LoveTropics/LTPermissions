package com.lovetropics.perms.protection.authority.shape;

import com.google.common.collect.Lists;
import com.lovetropics.perms.protection.EventSource;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.CombinedTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.TransformRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class TransformedShape implements AuthorityShape {
    private static final Codec<Transform> TRANSFORM_CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Transform, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<Pair<AffineTransform, T>> result = AFFINE_TRANSFORM_CODEC.decode(ops, input);
            if (result.result().isPresent()) {
                return result.map(pair -> pair.mapFirst(Function.identity()));
            }
            return COMBINED_TRANSFORM.decode(ops, input).map(pair -> pair.mapFirst(Function.identity()));
        }

        @Override
        public <T> DataResult<T> encode(Transform transform, DynamicOps<T> ops, T prefix) {
            if (transform instanceof AffineTransform affineTransform) {
                return AFFINE_TRANSFORM_CODEC.encode(affineTransform, ops, prefix);
            } else if (transform instanceof CombinedTransform combinedTransform) {
                return COMBINED_TRANSFORM.encode(combinedTransform, ops, prefix);
            }
            return DataResult.error(() -> "Unknown transform type " + transform.getClass().getSimpleName());
        }
    };

    private static final Codec<AffineTransform> AFFINE_TRANSFORM_CODEC = Codec.DOUBLE.listOf().comapFlatMap(
            list -> {
                if (list.size() != 12) {
                    return DataResult.error(() -> "Must have 12 elements");
                }

                double[] array = new double[list.size()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = list.get(i);
                }
                return DataResult.success(new AffineTransform(array));
            },
            transform -> Arrays.stream(transform.coefficients()).boxed().collect(Collectors.toList())
    );

    private static final Codec<CombinedTransform> COMBINED_TRANSFORM = TRANSFORM_CODEC.listOf()
            .xmap(
                    transforms -> new CombinedTransform(transforms.toArray(new Transform[0])),
                    transform -> Lists.newArrayList(TransformListGetter.apply(transform))
            );

    public static final MapCodec<TransformedShape> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AuthorityShape.CODEC.fieldOf("shape").forGetter(c -> c.shape),
            TRANSFORM_CODEC.fieldOf("transform").forGetter(c -> c.transform)
    ).apply(i, TransformedShape::new));

    private final AuthorityShape shape;
    private final Transform transform;

    private final UnaryOperator<BlockPos> inverseTransform;

    public TransformedShape(AuthorityShape shape, Transform transform) {
        this.shape = shape;
        this.transform = transform;

        Transform inverseTransform = transform.inverse();
        this.inverseTransform = pos -> {
            Vector3 vector = Vector3.at(pos.getX(), pos.getY(), pos.getZ());
            vector = inverseTransform.apply(vector);
            return BlockPos.containing(vector.x(), vector.y(), vector.z());
        };
    }

    @Override
    public boolean accepts(EventSource source) {
        return this.shape.accepts(EventSource.transform(source, this.inverseTransform));
    }

    @Override
    public MapCodec<TransformedShape> getCodec() {
        return CODEC;
    }

    @Nullable
    public static TransformedShape fromRegion(TransformRegion region) {
        AuthorityShape inner = WorldEditShapes.tryFromRegion(region.getRegion());
        if (inner != null) {
            return new TransformedShape(inner, region.getTransform());
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public Region tryIntoRegion(MinecraftServer server) {
        Region inner = this.shape.tryIntoRegion(server);
        if (inner == null) {
            return null;
        }

        return new TransformRegion(inner, this.transform);
    }

    static final class TransformListGetter {
        private static final MethodHandle GET_TRANSFORMS;

        static {
            try {
                Field field = CombinedTransform.class.getDeclaredField("transforms");
                field.setAccessible(true);
                GET_TRANSFORMS = MethodHandles.lookup().unreflectGetter(field);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to reflect CombinedTransform.transforms", e);
            }
        }

        static Transform[] apply(CombinedTransform region) {
            try {
                return (Transform[]) GET_TRANSFORMS.invokeExact(region);
            } catch (Throwable e) {
                throw new RuntimeException("Unable to get CombinedTransform.transforms", e);
            }
        }
    }
}
