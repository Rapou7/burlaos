package com.rapou.burlaos.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle custom particle effects
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    
    @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true)
    private void onAddBlockBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        // Example of controlling block breaking particles - can be extended later
    }

    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
    private void onAddBlockBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        // Example of controlling block break particles - can be extended later
    }
} 