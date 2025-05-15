package com.rapou.burlaos.mixins;

import com.rapou.burlaos.modules.JumpEffect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for LivingEntity to handle jump-related functionality
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    
    @Shadow public abstract boolean isClimbing();
    @Shadow protected boolean jumping;
    
    /**
     * Injection into the jump method to notify when a jump occurs
     */
    @Inject(method = "jump", at = @At("HEAD"))
    private void onJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && self == mc.player) {
            JumpEffect jumpEffect = Modules.get().get(JumpEffect.class);
            if (jumpEffect != null && jumpEffect.isActive()) {
                // The module will handle jump effects
            }
        }
    }
} 