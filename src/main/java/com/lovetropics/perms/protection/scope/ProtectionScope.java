package com.lovetropics.perms.protection.scope;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public interface ProtectionScope {
    static ProtectionScope global() {
        return GlobalScope.INSTANCE;
    }

    static ProtectionScope dimension(RegistryKey<World> dimension) {
        return new DimensionScope(dimension);
    }

    static ProtectionScope box(RegistryKey<World> dimension, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );

        return new BoxScope(dimension, min, max);
    }

    static ProtectionScope read(CompoundNBT root) {
        String type = root.getString("type");
        switch (type) {
            case "global": return global();
            case "dimension": {
                RegistryKey<World> dimension = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, new ResourceLocation(root.getString("dimension")));
                return dimension(dimension);
            }
            case "box": {
                RegistryKey<World> dimension = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, new ResourceLocation(root.getString("dimension")));
                BlockPos min = new BlockPos(root.getInt("min_x"), root.getInt("min_y"), root.getInt("min_z"));
                BlockPos max = new BlockPos(root.getInt("max_x"), root.getInt("max_y"), root.getInt("max_z"));
                return box(dimension, min, max);
            }
        }
        return global();
    }

    boolean contains(RegistryKey<World> dimension);

    boolean contains(RegistryKey<World> dimension, BlockPos pos);

    CompoundNBT write(CompoundNBT root);
}
