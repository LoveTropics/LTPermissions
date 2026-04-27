package com.lovetropics.perms.protection;

import com.lovetropics.perms.LTPermissions;
import com.lovetropics.perms.protection.authority.Authority;
import com.lovetropics.perms.protection.authority.behavior.AuthorityBehavior;
import com.lovetropics.perms.protection.authority.map.AuthorityMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class ProtectionPlayerTracker {
    public static final ProtectionPlayerTracker INSTANCE = new ProtectionPlayerTracker();

    private final Object2ObjectMap<UUID, Tracker> trackers = new Object2ObjectOpenHashMap<>();
    private final Set<UUID> queuedReset = new ObjectOpenHashSet<>();

    private final Set<AuthorityBehavior> lastBehaviors = new ReferenceArraySet<>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            INSTANCE.clearTracker(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INSTANCE.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INSTANCE.onPlayerChangeDimension(player);
        }
    }

    public void invalidate() {
        queuedReset.addAll(trackers.keySet());
    }

    private void onPlayerChangeDimension(ServerPlayer player) {
        final Tracker tracker = trackers.get(player.getUUID());
        if (tracker != null) {
            updateTracker(player, tracker);
        }
    }

    private void tickPlayer(final ServerPlayer player) {
        final UUID playerId = player.getUUID();
        if (queuedReset.remove(playerId)) {
            final Tracker tracker = trackers.get(playerId);
            if (tracker != null) {
                updateTracker(player, tracker);
                // We already updated the tracker state for this tick, so we don't need to check it again
                return;
            }
        }

        final Tracker tracker = getOrInitializeTracker(player);

        final long blockPos = player.blockPosition().asLong();
        if (blockPos != tracker.lastBlockPos) {
            updateTracker(player, tracker);
            tracker.lastBlockPos = blockPos;
        }
    }

    private Tracker getOrInitializeTracker(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Tracker tracker = trackers.get(playerId);
        if (tracker == null) {
            tracker = new Tracker();
            updateTracker(player, tracker);
            trackers.put(playerId, tracker);
        }
        return tracker;
    }

    // TODO: Create an index of regions in the world such that we can know that within a given area, behaviors will never change, and search domain can be smaller
    private void updateTracker(final ServerPlayer player, final Tracker tracker) {
        final ProtectionManager protection = ProtectionManager.get(player.level().getServer());
        tracker.lastBlockPos = player.blockPosition().asLong();

        try {
            lastBehaviors.addAll(tracker.behaviors);
            tracker.behaviors.clear();

            final AuthorityMap<Authority> authorities = protection.selectWithBehavior(player.level().dimension());
            if (authorities != null) {
                final EventSource source = EventSource.forEntity(player);
                for (final Authority authority : authorities) {
                    if (authority.eventFilter().accepts(source)) {
                        tracker.behaviors.add(authority.behavior().getBehavior());
                    }
                }
            }

            for (final AuthorityBehavior behavior : lastBehaviors) {
                if (!tracker.behaviors.contains(behavior)) {
                    behavior.onPlayerExit(player);
                }
            }

            for (final AuthorityBehavior behavior : tracker.behaviors) {
                if (!lastBehaviors.contains(behavior)) {
                    behavior.onPlayerEnter(player);
                }
            }
        } finally {
            lastBehaviors.clear();
        }
    }

    private void clearTracker(ServerPlayer player) {
        Tracker tracker = trackers.remove(player.getUUID());
        if (tracker != null) {
            for (AuthorityBehavior behavior : tracker.behaviors) {
                behavior.onPlayerExit(player);
            }
        }
    }

    private static final class Tracker {
        private final Set<AuthorityBehavior> behaviors = new ReferenceArraySet<>();
        private long lastBlockPos = -1;
    }
}
