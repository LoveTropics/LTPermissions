package com.lovetropics.perms.mixin;

import com.lovetropics.perms.CommandAliasConfiguration;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesMixin {
    @Inject(method = "lambda$loadResources$2", at = @At("HEAD"))
    private static void beforeLoadResources(ReloadableServerRegistries.LoadResult fullRegistries, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, List updatedContextTags, PermissionSet functionCompilationPermissions, ResourceManager resourceManager, Executor backgroundExecutor, Executor mainThreadExecutor, List pendingComponents, CallbackInfoReturnable<CompletionStage> cir) {
        CommandAliasConfiguration.setResourceManager(resourceManager);
    }

    @Inject(method = "lambda$loadResources$2", at = @At("TAIL"))
    private static void afterLoadResources(ReloadableServerRegistries.LoadResult fullRegistries, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, List updatedContextTags, PermissionSet functionCompilationPermissions, ResourceManager resourceManager, Executor backgroundExecutor, Executor mainThreadExecutor, List pendingComponents, CallbackInfoReturnable<CompletionStage> cir) {
        CommandAliasConfiguration.clearResourceManager();
    }
}
