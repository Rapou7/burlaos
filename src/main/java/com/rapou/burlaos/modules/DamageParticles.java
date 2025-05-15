package com.rapou.burlaos.modules;

import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import com.rapou.burlaos.burlaos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class DamageParticles extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General settings
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the damage particles.")
        .defaultValue(2.5)
        .min(0.5)
        .max(5.0)
        .build()
    );

    private final Setting<Double> timeToLive = sgGeneral.add(new DoubleSetting.Builder()
        .name("time-to-live")
        .description("How long the particles live for in seconds.")
        .defaultValue(2.0)
        .min(0.5)
        .max(5.0)
        .build()
    );

    private final Setting<Double> transitionY = sgGeneral.add(new DoubleSetting.Builder()
        .name("transition-y")
        .description("How far the particles move up.")
        .defaultValue(1.5)
        .min(-2.0)
        .max(2.0)
        .build()
    );

    private final Setting<Boolean> showAtCrosshair = sgGeneral.add(new BoolSetting.Builder()
        .name("show-at-crosshair")
        .description("Whether to show particles at crosshair hit position instead of above entity.")
        .defaultValue(true)
        .build()
    );

    // Color settings
    private final Setting<SettingColor> increasedHealthColor = sgColors.add(new ColorSetting.Builder()
        .name("increased-health-color")
        .description("The color of the damage particles when an entity gains health.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> decreasedHealthColor = sgColors.add(new ColorSetting.Builder()
        .name("decreased-health-color")
        .description("The color of the damage particles when an entity loses health.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Object2FloatOpenHashMap<LivingEntity> healthMap = new Object2FloatOpenHashMap<>();
    private final ArrayDeque<Particle> particles = new ArrayDeque<>();
    private final Map<LivingEntity, Vec3d> lastHitPositions = new HashMap<>();

    private static final float EPSILON = 0.05F;
    private static final String FORMATTER = "%.1f";

    public DamageParticles() {
        super(burlaos.CATEGORY, "damage-particles", "Displays particles showing damage dealt to entities.");
    }

    @Override
    public void onDeactivate() {
        healthMap.clear();
        particles.clear();
        lastHitPositions.clear();
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        healthMap.clear();
        particles.clear();
        lastHitPositions.clear();
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        healthMap.removeFloat(event.entity);
        lastHitPositions.remove(event.entity);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        // Update hit positions from crosshair
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
            if (entityHit.getEntity() instanceof LivingEntity) {
                LivingEntity hitEntity = (LivingEntity) entityHit.getEntity();
                lastHitPositions.put(hitEntity, entityHit.getPos());
            }
        }

        // Collect all living entities except the player
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity == mc.player) continue;
            
            LivingEntity livingEntity = (LivingEntity) entity;
            float currentHealth = livingEntity.getHealth();

            if (healthMap.containsKey(livingEntity)) {
                float prevHealth = healthMap.getFloat(livingEntity);
                float delta = Math.abs(prevHealth - currentHealth);
                if (delta > EPSILON) {
                    // Determine particle position
                    Vec3d particlePos;
                    
                    if (showAtCrosshair.get() && lastHitPositions.containsKey(livingEntity)) {
                        // Use last hit position from crosshair
                        particlePos = lastHitPositions.get(livingEntity);
                    } else {
                        // Default: calculate position above entity's head
                        Vec3d entityPos = livingEntity.getPos();
                        double entityHeight = livingEntity.getHeight() + 0.5; // Add offset above head
                        particlePos = new Vec3d(entityPos.x, entityPos.y + entityHeight, entityPos.z);
                    }
                    
                    particles.add(new Particle(
                        System.currentTimeMillis(),
                        String.format(FORMATTER, delta),
                        prevHealth > currentHealth ? decreasedHealthColor.get() : increasedHealthColor.get(),
                        particlePos,
                        livingEntity
                    ));
                }
            }

            healthMap.put(livingEntity, currentHealth);
        }

        // Remove dead or missing entities
        healthMap.object2FloatEntrySet().removeIf(entry -> 
            !(entry.getKey() instanceof Entity) || ((Entity) entry.getKey()).isRemoved() || 
            entry.getKey().isDead());

        // Remove expired particles
        long currentTime = System.currentTimeMillis();
        long expiryTime = currentTime - (long) (timeToLive.get() * 1000);
        
        while (!particles.isEmpty() && particles.peek().startTime < expiryTime) {
            particles.poll();
        }
    }

    // Use 2D rendering for the particles since 3D text is problematic
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || particles.isEmpty()) return;
        
        long currentTime = System.currentTimeMillis();
        
        for (Particle particle : particles) {
            // Skip if entity is no longer valid
            if (particle.entity == null || !particle.entity.isAlive()) continue;
            
            // Calculate progress (0.0 to 1.0)
            float progress = (float)((currentTime - particle.startTime) / (timeToLive.get() * 1000));
            if (progress > 1.0f) continue;
            
            // Calculate vertical offset based on progress
            double yOffset = transitionY.get() * progress;
            Vec3d pos = new Vec3d(particle.pos.x, particle.pos.y + yOffset, particle.pos.z);
            
            // Convert to Vector3d for NametagUtils (which uses joml Vector3d)
            Vector3d vector3d = new Vector3d(pos.x, pos.y, pos.z);
            
            // Fade out color based on progress
            Color color = particle.color;
            int alpha = (int) (color.a * (1.0f - progress));
            color = new Color(color.r, color.g, color.b, alpha);
            
            // Calculate size
            double textSize = scale.get() * (1.0f - progress * 0.5f);
            
            // Check if we should show the nametag
            if (!NametagUtils.to2D(vector3d, textSize)) continue;
            
            // Set render state
            NametagUtils.begin(vector3d);
            
            // Draw text in 2D
            TextRenderer.get().begin(textSize);
            TextRenderer.get().render(particle.text, -TextRenderer.get().getWidth(particle.text) / 2, 0, color);
            TextRenderer.get().end();
            
            // End render state
            NametagUtils.end();
        }
    }

    private static class Particle {
        final long startTime;
        final String text;
        final Color color;
        final Vec3d pos;
        final LivingEntity entity;

        Particle(long startTime, String text, Color color, Vec3d pos, LivingEntity entity) {
            this.startTime = startTime;
            this.text = text;
            this.color = color;
            this.pos = pos;
            this.entity = entity;
        }
    }
} 