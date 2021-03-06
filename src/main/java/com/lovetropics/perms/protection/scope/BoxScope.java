package com.lovetropics.perms.protection.scope;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BoxScope implements ProtectionScope {
    private final RegistryKey<World> dimension;
    private final BlockPos min;
    private final BlockPos max;

    public BoxScope(RegistryKey<World> dimension, BlockPos min, BlockPos max) {
        this.dimension = dimension;
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean contains(RegistryKey<World> dimension) {
        return this.dimension == dimension;
    }

    @Override
    public boolean contains(RegistryKey<World> dimension, BlockPos pos) {
        return this.dimension == dimension
                && pos.getX() >= this.min.getX() && pos.getY() >= this.min.getY() && pos.getZ() >= this.min.getZ()
                && pos.getX() <= this.max.getX() && pos.getY() <= this.max.getY() && pos.getZ() <= this.max.getZ();
    }

    @Override
    public CompoundNBT write(CompoundNBT root) {
        root.putString("type", "box");
        root.putString("dimension", this.dimension.getLocation().toString());
        root.putInt("min_x", this.min.getX());
        root.putInt("min_y", this.min.getY());
        root.putInt("min_z", this.min.getZ());
        root.putInt("max_x", this.max.getX());
        root.putInt("max_y", this.max.getY());
        root.putInt("max_z", this.max.getZ());
        return root;
    }
}
