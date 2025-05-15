package com.rapou.burlaos.mixins;

import com.rapou.burlaos.modules.AspectRatio;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    
    @ModifyArgs(
        method = "getBasicProjectionMatrix",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;")
    )
    private void hookBasicProjectionMatrix(Args args) {
        AspectRatio aspectRatio = Modules.get().get(AspectRatio.class);
        if (aspectRatio != null && aspectRatio.isActive()) {
            args.set(1, (float) args.get(1) / aspectRatio.getRatioMultiplier());
        }
    }
} 