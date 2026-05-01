package com.lovetropics.perms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lovetropics.lib.permission.PermissionsApi;
import com.lovetropics.lib.permission.role.RoleReader;
import com.lovetropics.perms.LTPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.waypoints.Waypoint;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @WrapOperation(method = "makeWaypointConnectionWith", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;locatorBarIcon:Lnet/minecraft/world/waypoints/Waypoint$Icon;", opcode = Opcodes.GETFIELD))
    private Waypoint.Icon modifyWaypointIcon(LivingEntity entity, Operation<Waypoint.Icon> original) {
        if (entity instanceof ServerPlayer serverPlayer) {
            RoleReader roles = PermissionsApi.lookup().byPlayer(serverPlayer);
            Waypoint.Icon icon = roles.overrides().getOrNull(LTPermissions.WAYPOINT_ICON);
            if (icon != null) {
                return icon;
            }
        }
        return original.call(entity);
    }
}
