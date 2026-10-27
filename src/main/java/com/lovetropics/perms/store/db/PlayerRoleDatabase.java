package com.lovetropics.perms.store.db;

import com.lovetropics.perms.store.PackedRoles;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
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

    public Optional<PackedRoles> tryLoad(UUID uuid) {
        try {
            ByteBuffer bytes = binary.get(uuid);
            if (bytes != null) {
                try {
                    return Optional.of(deserializeRoles(bytes));
                } catch (IOException e) {
                    LOGGER.error("Failed to deserialize roles for {}, dropping", uuid, e);
                    binary.remove(uuid);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load roles for {}", uuid, e);
        }
        return Optional.empty();
    }

    public void trySave(UUID uuid, PackedRoles roles) {
        try {
            if (!roles.isEmpty()) {
                ByteBuffer bytes = serializeRoles(roles);
                binary.put(uuid, bytes);
            } else {
                binary.remove(uuid);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save roles for {}", uuid, e);
        }
    }

    private static ByteBuffer serializeRoles(PackedRoles roles) throws IOException {
        CompoundTag nbt = (CompoundTag) PackedRoles.CODEC.encodeStart(NbtOps.INSTANCE, roles).getOrThrow(IOException::new);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(nbt, output);
            return ByteBuffer.wrap(output.toByteArray());
        }
    }

    private static PackedRoles deserializeRoles(ByteBuffer bytes) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes.array())) {
            CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            return PackedRoles.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(IOException::new);
        }
    }

    public Set<UUID> knownPlayerIds() {
        return binary.knownKeys();
    }

    @Override
    public void close() throws IOException {
        binary.close();
    }
}
