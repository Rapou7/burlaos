package com.rapou.burlaos.modules;

import com.rapou.burlaos.burlaos;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Iterator;

public class JumpEffect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General settings
    private final Setting<Double> endRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("end-radius")
        .description("The end radius of the jump effect.")
        .defaultValue(1.5)
        .min(0.1)
        .max(5.0)
        .build()
    );

    private final Setting<Double> startRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("start-radius")
        .description("The start radius of the jump effect.")
        .defaultValue(0.5)
        .min(0.0)
        .max(3.0)
        .build()
    );

    private final Setting<Double> lineWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("line-width")
        .description("The width of the circle lines.")
        .defaultValue(5.0)
        .min(0.5)
        .max(15.0)
        .build()
    );

    private final Setting<Integer> segments = sgGeneral.add(new IntSetting.Builder()
        .name("segments")
        .description("Number of line segments to form the circle.")
        .defaultValue(48)
        .min(12)
        .max(128)
        .sliderRange(12, 128)
        .build()
    );

    private final Setting<Integer> lifetime = sgGeneral.add(new IntSetting.Builder()
        .name("lifetime")
        .description("How long the effect lasts in ticks.")
        .defaultValue(20)
        .min(1)
        .max(40)
        .build()
    );

    private final Setting<Integer> hueOffset = sgGeneral.add(new IntSetting.Builder()
        .name("hue-offset")
        .description("Hue offset for animation.")
        .defaultValue(63)
        .range(-360, 360)
        .sliderRange(-360, 360)
        .build()
    );

    // Color settings
    private final Setting<SettingColor> circleColor = sgColors.add(new ColorSetting.Builder()
        .name("circle-color")
        .description("The color of the jump effect circle.")
        .defaultValue(new SettingColor(0, 255, 4, 180))
        .build()
    );

    private final ArrayDeque<JumpCircle> circles = new ArrayDeque<>();
    private boolean wasOnGround = false;

    public JumpEffect() {
        super(burlaos.CATEGORY, "jump-effect", "Displays a circle effect when you jump.");
    }

    /**
     * Force creates a jump circle at the player's position
     * Can be called from mixins to directly trigger the effect
     */
    public void forceCreateJumpCircle() {
        if (mc.player != null && isActive()) {
            circles.add(new JumpCircle(mc.player.getPos(), 0));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Process existing circles
        Iterator<JumpCircle> iterator = circles.iterator();
        while (iterator.hasNext()) {
            JumpCircle circle = iterator.next();
            circle.age++;
            if (circle.age >= lifetime.get()) {
                iterator.remove();
            }
        }

        // Detect jump (when player was on ground and now isn't)
        if (wasOnGround && !mc.player.isOnGround() && mc.player.getVelocity().y > 0) {
            circles.add(new JumpCircle(mc.player.getPos(), 0));
        }

        wasOnGround = mc.player.isOnGround();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || circles.isEmpty()) return;

        // Save current OpenGL state
        float originalLineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        
        // Set thicker line width for better visibility
        RenderSystem.lineWidth(lineWidth.get().floatValue());

        for (JumpCircle circle : circles) {
            float progress = (float) circle.age / lifetime.get();
            progress = Math.min(1.0f, Math.max(0.0f, progress));
            
            // Calculate radius based on progress
            double radius = startRadius.get() + (endRadius.get() - startRadius.get()) * progress;
            
            // Calculate colors with fade
            Color color = applyFade(circleColor.get(), progress);
            
            // Draw a simple horizontal circle at the player's position
            int segmentsCount = segments.get();
            double angleStep = 2 * Math.PI / segmentsCount;
            
            // Draw outer circle with lines
            for (int i = 0; i < segmentsCount; i++) {
                double angle1 = i * angleStep;
                double angle2 = (i + 1) * angleStep;
                
                double x1 = circle.pos.x + radius * Math.cos(angle1);
                double z1 = circle.pos.z + radius * Math.sin(angle1);
                
                double x2 = circle.pos.x + radius * Math.cos(angle2);
                double z2 = circle.pos.z + radius * Math.sin(angle2);
                
                // Draw a line segment slightly elevated to be visible on ground
                double elevationY = 0.02; // Small offset to ensure it's visible above ground
                event.renderer.line(
                    x1, circle.pos.y + elevationY, z1, 
                    x2, circle.pos.y + elevationY, z2, 
                    color
                );
            }
        }
        
        // Restore original line width
        RenderSystem.lineWidth(originalLineWidth);
    }

    private Color applyFade(SettingColor baseColor, float progress) {
        // Apply fade (transparency decreases with progress)
        float alpha = (1.0f - progress) * baseColor.a / 255.0f;
        
        Color color = new Color(baseColor.r, baseColor.g, baseColor.b, (int)(alpha * 255));
        
        // Apply hue shift if enabled
        if (hueOffset.get() != 0) {
            float[] hsb = java.awt.Color.RGBtoHSB(color.r, color.g, color.b, null);
            hsb[0] += (hueOffset.get() * progress) / 360.0f;
            hsb[0] = hsb[0] % 1.0f;
            
            java.awt.Color shifted = java.awt.Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
            color = new Color(shifted.getRed(), shifted.getGreen(), shifted.getBlue(), color.a);
        }
        
        return color;
    }

    private static class JumpCircle {
        final Vec3d pos;
        int age;

        JumpCircle(Vec3d pos, int age) {
            this.pos = pos;
            this.age = age;
        }
    }
} 