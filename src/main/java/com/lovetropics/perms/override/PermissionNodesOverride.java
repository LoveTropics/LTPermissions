package com.lovetropics.perms.override;

import com.lovetropics.lib.permission.PermissionNodeHandler;
import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionType;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LTPermissions.ID)
public record PermissionNodesOverride(
        Map<String, PermissionValue> values
) {
    public static final Codec<PermissionNodesOverride> CODEC = Codec.unboundedMap(Codec.STRING, PermissionValue.CODEC).xmap(PermissionNodesOverride::new, PermissionNodesOverride::values);

    public static final PermissionNodesOverride EMPTY = new PermissionNodesOverride(Map.of());

    public static PermissionNodesOverride build(List<PermissionNodesOverride> overrides) {
        Map<String, PermissionValue> values = new HashMap<>();
        for (PermissionNodesOverride override : overrides) {
            for (Map.Entry<String, PermissionValue> entry : override.values.entrySet()) {
                // Topmost entry gets priority
                values.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return new PermissionNodesOverride(Map.copyOf(values));
    }

    @SubscribeEvent
    public static void gatherHandlers(PermissionGatherEvent.Handler event) {
        PermissionNodeHandler.register(event, 0, new PermissionHandler());
    }

    public sealed interface PermissionValue {
        Codec<PermissionValue> CODEC = Codec.either(
                Codec.either(BoolValue.CODEC, IntValue.CODEC),
                Codec.either(StringValue.CODEC, ComponentValue.CODEC)
        ).xmap(
                e -> e.map(
                        Either::unwrap,
                        Either::unwrap
                ),
                value -> switch (value) {
                    case BoolValue bool -> Either.left(Either.left(bool));
                    case IntValue integer -> Either.left(Either.right(integer));
                    case StringValue string -> Either.right(Either.left(string));
                    case ComponentValue component -> Either.right(Either.right(component));
                }
        );

        static <T> @Nullable T tryCast(PermissionType<T> type, Object value) {
            if (type.typeToken().isInstance(value)) {
                return type.typeToken().cast(value);
            }
            return null;
        }

        <T> @Nullable T resolve(PermissionType<T> type);

        record BoolValue(boolean value) implements PermissionValue {
            public static final Codec<BoolValue> CODEC = Codec.BOOL.xmap(BoolValue::new, BoolValue::value);

            @Override
            public @Nullable <T> T resolve(PermissionType<T> type) {
                return tryCast(type, value);
            }
        }

        record IntValue(int value) implements PermissionValue {
            public static final Codec<IntValue> CODEC = Codec.INT.xmap(IntValue::new, IntValue::value);

            @Override
            public @Nullable <T> T resolve(PermissionType<T> type) {
                return tryCast(type, value);
            }
        }

        record StringValue(String value) implements PermissionValue {
            public static final Codec<StringValue> CODEC = Codec.STRING.xmap(StringValue::new, StringValue::value);

            @Override
            @SuppressWarnings("unchecked")
            public @Nullable <T> T resolve(PermissionType<T> type) {
                if (type == PermissionTypes.COMPONENT) {
                    return (T) Component.literal(value);
                }
                return tryCast(type, value);
            }
        }

        record ComponentValue(Component value) implements PermissionValue {
            public static final Codec<ComponentValue> CODEC = ComponentSerialization.CODEC.xmap(ComponentValue::new, ComponentValue::value);

            @Override
            public @Nullable <T> T resolve(PermissionType<T> type) {
                return tryCast(type, value);
            }
        }
    }

    private static class PermissionHandler implements PermissionNodeHandler {
        @Override
        public <T> @Nullable T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            RoleReader roles = PermissionsApi.lookup().byPlayer(player);
            return resolveOverrideValue(roles, node.getType(), node.getNodeName());
        }

        @Override
        public <T> @Nullable T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            RoleReader roles = PermissionsApi.lookup().byPlayerId(player);
            return resolveOverrideValue(roles, node.getType(), node.getNodeName());
        }

        private static <T> @Nullable T resolveOverrideValue(RoleReader roles, PermissionType<T> nodeType, String nodeId) {
            PermissionNodesOverride overrides = roles.overrides().get(LTPermissions.PERMISSION_NODES, EMPTY);
            PermissionValue value = overrides.values().get(nodeId);
            return value != null ? value.resolve(nodeType) : null;
        }
    }
}
