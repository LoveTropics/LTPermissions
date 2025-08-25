package com.lovetropics.perms.store.db;

import com.lovetropics.perms.config.RolesConfig;
import com.lovetropics.perms.store.PlayerRoleSet;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.UUID;

public final class PlayerRoleDatabase implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Uuid2BinaryDatabase binary;

    private PlayerRoleDatabase(Uuid2BinaryDatabase binary) {
        this.binary = binary;
    }

    public static PlayerRoleDatabase open(Path path) throws IOException {
        Uuid2BinaryDatabase binary = Uuid2BinaryDatabase.open(path);
        return new PlayerRoleDatabase(binary);
    }

    public void tryLoadInto(UUID uuid, PlayerRoleSet roles) {
        try {
            ByteBuffer bytes = this.binary.get(uuid);
            if (bytes != null) {
                try {
                    deserializeRoles(roles, bytes);
                } catch (IOException e) {
                    LOGGER.error("Failed to deserialize roles for {}, dropping", uuid, e);
                    this.binary.remove(uuid);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load roles for {}", uuid, e);
        }
    }

    public void trySave(UUID uuid, PlayerRoleSet roles) {
        try {
            if (!roles.isEmpty()) {
                ByteBuffer bytes = serializeRoles(roles);
                this.binary.put(uuid, bytes);
            } else {
                this.binary.remove(uuid);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save roles for {}", uuid, e);
        }
    }

    private static ByteBuffer serializeRoles(PlayerRoleSet roles) throws IOException {
        CompoundTag nbt = new CompoundTag();
        nbt.put("roles", roles.serialize());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(nbt, output);
            return ByteBuffer.wrap(output.toByteArray());
        }
    }

    private static void deserializeRoles(PlayerRoleSet roles, ByteBuffer bytes) throws IOException {
        RolesConfig config = RolesConfig.get();

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes.array())) {
            CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            roles.deserialize(config, nbt.getListOrEmpty("roles"));
        }
    }

    @Override
    public void close() throws IOException {
        this.binary.close();
    }
}
