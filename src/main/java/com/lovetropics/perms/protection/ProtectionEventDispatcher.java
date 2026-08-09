package com.lovetropics.perms.protection;

import com.lovetropics.perms.LTPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.TriState;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = LTPermissions.ID)
public final class ProtectionEventDispatcher {
    @SubscribeEvent
    public static void onTickPlayer(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FoodData food = player.getFoodData();
            if (food.needsFood()) {
                ProtectionManager protect = protect(player.level());
                EventSource source = EventSource.forEntity(player);
                if (protect.denies(source, ProtectionRule.HUNGER)) {
                    food.setFoodLevel(20);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreakBlock(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.forEntityAt(event.getPlayer(), event.getPos());
            if (protect.denies(source, ProtectionRule.BREAK)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onTrampleFarmland(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.forEntityAt(event.getEntity(), event.getPos());
            if (protect.denies(source, ProtectionRule.BREAK)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.forEntityAt(event.getEntity(), event.getPos());
            if (protect.denies(source, ProtectionRule.INTERACT) || protect.denies(source, ProtectionRule.INTERACT_BLOCKS)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.forEntityAt(event.getEntity(), event.getPos());
            if (protect.denies(source, ProtectionRule.INTERACT)) {
                event.setCanceled(true);
                return;
            }

            if (protect.denies(source, ProtectionRule.INTERACT_BLOCKS)) {
                event.setUseBlock(TriState.FALSE);
            }
            if (protect.denies(source, ProtectionRule.INTERACT_ITEMS) || (placesBlock(event) && protect.denies(source, ProtectionRule.PLACE))) {
                event.setUseItem(TriState.FALSE);
            }

            final BlockState state = level.getBlockState(event.getPos());
            if (state.is(Blocks.CHISELED_BOOKSHELF) && protect.denies(source, ProtectionRule.MODIFY_BOOKSHELVES, ProtectionRule.MODIFY)) {
                event.setUseBlock(TriState.FALSE);
            }
        }
    }

    private static boolean placesBlock(PlayerInteractEvent.RightClickBlock event) {
        Item item = event.getItemStack().getItem();
        return item instanceof BlockItem || item instanceof FlintAndSteelItem;
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.forEntityAt(event.getEntity(), event.getPos());
            if (protect.denies(source, ProtectionRule.INTERACT) || protect.denies(source, ProtectionRule.INTERACT_ITEMS)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (isInteractionBlocked(event.getEntity(), event.getTarget(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractEntitySpecific(PlayerInteractEvent.EntityInteract event) {
        if (isInteractionBlocked(event.getEntity(), event.getTarget(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static boolean isInteractionBlocked(Entity entity, Entity target, BlockPos pos) {
        if (entity.level() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.forEntityAt(entity, pos);
            if (protect.denies(source, ProtectionRule.INTERACT) || protect.denies(source, ProtectionRule.INTERACT_ENTITIES)) {
                return true;
            }

            if (target instanceof ItemFrame && protect.denies(source, ProtectionRule.MODIFY_ITEM_FRAMES, ProtectionRule.MODIFY)) {
                return true;
            } else if (target instanceof ArmorStand && protect.denies(source, ProtectionRule.MODIFY_ARMOR_STANDS, ProtectionRule.MODIFY)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (event.getEntity() instanceof ServerPlayer player && target.level() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.forEntityAt(player, target.blockPosition());
            if (protect.denies(source, ProtectionRule.ATTACK)) {
                event.setCanceled(true);
                return;
            } else if (target instanceof Player && protect.denies(source, ProtectionRule.PVP)) {
                event.setCanceled(true);
                return;
            }

            if (event.getTarget() instanceof ItemFrame && protect.denies(source, ProtectionRule.MODIFY_ITEM_FRAMES, ProtectionRule.MODIFY)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);

            EventSource source = EventSource.forEntity(entity);
            if (protect.denies(source, ProtectionRule.DAMAGE)) {
                event.setNewDamage(0);
                return;
            }

            if (entity instanceof Player && protect.denies(source, ProtectionRule.PLAYER_DAMAGE)) {
                event.setNewDamage(0);
                return;
            }

            if ((event.getSource().is(DamageTypeTags.IS_FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL)) && protect.denies(source, ProtectionRule.FALL_DAMAGE)) {
                event.setNewDamage(0);
            }
        }
    }

    @SubscribeEvent
    public static void onSpawnPortal(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            EventSource source = EventSource.at(level, event.getPos());
            if (protect.denies(source, ProtectionRule.PORTALS)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            Explosion explosion = event.getExplosion();
            EventSource source = EventSource.forOptionalEntityAt(level, explosion.getIndirectSourceEntity(), BlockPos.containing(explosion.center()));
            if (protect.denies(source, ProtectionRule.EXPLOSION)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ProtectionManager protect = protect(level);
            Explosion explosion = event.getExplosion();
            EventSource source = EventSource.forOptionalEntityAt(level, explosion.getIndirectSourceEntity(), BlockPos.containing(explosion.center()));
            // Approximation - ideally we'd check each block rather than just the center, but it's  performance heavy. We might revisit it later.
            if (protect.denies(source, ProtectionRule.BREAK)) {
                event.getAffectedBlocks().clear();
            }
        }
    }

    public static boolean onEditSign(final ServerPlayer player, final BlockPos pos) {
        final ServerLevel level = player.level();
        final ProtectionManager protect = protect(level);
        final EventSource source = EventSource.forEntityAt(player, pos);
        return protect.denies(source, ProtectionRule.MODIFY_SIGNS, ProtectionRule.MODIFY);
    }

    public static boolean onRemoveBookFromLectern(final ServerPlayer player) {
        final ProtectionManager protect = protect(player.level());
        // TODO: The position checked here isn't quite correct, as we don't have the context for where the Lectern is
        final EventSource source = EventSource.forEntity(player);
        return protect.denies(source, ProtectionRule.MODIFY_LECTERNS, ProtectionRule.MODIFY);
    }

    public static boolean onCraft(final ServerPlayer player) {
        final ProtectionManager protect = protect(player.level());
        final EventSource source = EventSource.forEntity(player);
        return protect.denies(source, ProtectionRule.CRAFT);
    }

    private static ProtectionManager protect(ServerLevel level) {
        return ProtectionManager.get(level.getServer());
    }
}
