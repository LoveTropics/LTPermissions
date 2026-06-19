package com.lovetropics.perms.override;

import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@EventBusSubscriber(modid = LTPermissions.ID)
public record NameDecorationOverride(
        Optional<AddPrefix> prefix,
        Optional<AddSuffix> suffix,
        Optional<ApplyStyle> applyStyle,
        boolean inTabList
) {
    private static final NameDecorationOverride EMPTY = new NameDecorationOverride(Optional.empty(), Optional.empty(), Optional.empty(), false);

    public static final Codec<NameDecorationOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
            AddPrefix.CODEC.optionalFieldOf("prefix").forGetter(NameDecorationOverride::prefix),
            AddSuffix.CODEC.optionalFieldOf("suffix").forGetter(NameDecorationOverride::suffix),
            ApplyStyle.CODEC.optionalFieldOf("style").forGetter(NameDecorationOverride::applyStyle),
            Codec.BOOL.optionalFieldOf("in_tab_list", true).forGetter(NameDecorationOverride::inTabList)
    ).apply(i, NameDecorationOverride::new));

    public Component apply(MutableComponent name) {
        if (applyStyle.isPresent()) {
            name = applyStyle.get().apply(name);
        }
        if (prefix.isPresent()) {
            name = prefix.get().apply(name);
        }
        if (suffix.isPresent()) {
            name = suffix.get().apply(name);
        }
        return name;
    }

    public static NameDecorationOverride build(List<NameDecorationOverride> overrides) {
        if (overrides.isEmpty()) {
            return EMPTY;
        }

        Optional<AddPrefix> prefix = join(overrides.stream().flatMap(override -> override.prefix().stream()).map(AddPrefix::prefix)).map(AddPrefix::new);
        Optional<AddSuffix> suffix = join(overrides.stream().flatMap(override -> override.suffix().stream()).map(AddSuffix::suffix)).map(AddSuffix::new);
        Optional<ApplyStyle> style = overrides.get(0).applyStyle();
        boolean inTabList = overrides.stream().anyMatch(NameDecorationOverride::inTabList);
        return new NameDecorationOverride(prefix, suffix, style, inTabList);
    }

    private static Optional<Component> join(Stream<Component> stream) {
        List<Component> components = stream.toList();
        if (components.isEmpty()) {
            return Optional.empty();
        }
        MutableComponent result = Component.empty();
        components.forEach(result::append);
        return Optional.of(result);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // TODO: temporary patch to make sure name style updates! in cases like teams changing we aren't refreshing.
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.tickCount % (SharedConstants.TICKS_PER_SECOND * 10) == 0) {
                player.refreshDisplayName();
                player.refreshTabListName();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFormatName(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            final Component displayName = formatDisplayName(player, event.getDisplayname());
            if (displayName != null) {
                event.setDisplayname(displayName);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFormatName(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            final Component displayName = formatDisplayName(player, player.getName());
            if (displayName != null) {
                event.setDisplayName(displayName);
            }
        }
    }

    @Nullable
    private static Component formatDisplayName(final ServerPlayer player, final Component name) {
        if (name.getStyle().getColor() != null || hasTeamColor(player)) {
            return null;
        }
        final RoleReader roles = PermissionsApi.lookup().byPlayer(player);
        final NameDecorationOverride nameDecoration = roles.overrides().getOrNull(LTPermissions.NAME_DECORATION);
        if (nameDecoration != null) {
            return nameDecoration.apply(name.copy());
        }
        return null;
    }

    private static boolean hasTeamColor(ServerPlayer player) {
        if (player.getTeam() instanceof PlayerTeam team) {
            return team.getColor().isPresent();
        }
        return false;
    }

    public record AddPrefix(Component prefix) {
        public static final Codec<AddPrefix> CODEC = ComponentSerialization.CODEC.xmap(AddPrefix::new, AddPrefix::prefix);

        public MutableComponent apply(final MutableComponent name) {
            return Component.empty().append(this.prefix).append(name);
        }
    }

    public record AddSuffix(Component suffix) {
        public static final Codec<AddSuffix> CODEC = ComponentSerialization.CODEC.xmap(AddSuffix::new, AddSuffix::suffix);

        public MutableComponent apply(final MutableComponent name) {
            return name.append(suffix);
        }
    }

    public record ApplyStyle(Either<Style, TextColor> styleOrColor) {
        public static final Codec<ApplyStyle> CODEC = Codec.either(Style.Serializer.CODEC, TextColor.CODEC)
                .xmap(ApplyStyle::new, ApplyStyle::styleOrColor);

        public MutableComponent apply(final MutableComponent text) {
            return text.setStyle(applyStyle(text.getStyle()));
        }

        private Style applyStyle(Style style) {
            return this.styleOrColor.map(style::applyTo, style::withColor);
        }
    }
}
