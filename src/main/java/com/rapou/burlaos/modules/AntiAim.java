package com.rapou.burlaos.modules;

import com.rapou.burlaos.burlaos;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.item.Items;
import net.minecraft.item.CrossbowItem;

import java.util.Comparator;
import java.util.Random;

public class AntiAim extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgJitter = settings.createGroup("Jitter");
    private final SettingGroup sgWall = settings.createGroup("Wall Detection");
    private final SettingGroup sgWeapons = settings.createGroup("Weapon Control");

    // General settings
    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("spin-speed")
            .description("The speed at which you spin.")
            .defaultValue(30)
            .sliderMin(0.0)
            .sliderMax(50.0)
            .build()
    );

    private final Setting<Boolean> targetBased = sgGeneral.add(new BoolSetting.Builder()
            .name("target-based")
            .description("Hides your head from the nearest player.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("target-range")
            .description("The range to search for targets.")
            .defaultValue(15)
            .min(1)
            .sliderMax(30)
            .visible(targetBased::get)
            .build()
    );

    private final Setting<Double> downAngle = sgGeneral.add(new DoubleSetting.Builder()
            .name("down-angle")
            .description("How far down your head should look.")
            .defaultValue(80)
            .range(0, 90)
            .sliderMax(90)
            .build()
    );

    // Jitter settings
    private final Setting<Boolean> jitter = sgJitter.add(new BoolSetting.Builder()
            .name("jitter")
            .description("Adds random jitter to your rotations.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> jitterIntensity = sgJitter.add(new DoubleSetting.Builder()
            .name("jitter-intensity")
            .description("How much to jitter your head position.")
            .defaultValue(15)
            .min(1)
            .sliderMax(45)
            .visible(jitter::get)
            .build()
    );

    private final Setting<Integer> jitterDelay = sgJitter.add(new IntSetting.Builder()
            .name("jitter-delay")
            .description("Delay between jitter movements in ticks.")
            .defaultValue(3)
            .min(1)
            .sliderMax(20)
            .visible(jitter::get)
            .build()
    );

    // Wall settings
    private final Setting<Boolean> useWalls = sgWall.add(new BoolSetting.Builder()
            .name("use-walls")
            .description("Hide your head behind walls when possible.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> wallPeek = sgWall.add(new BoolSetting.Builder()
            .name("wall-peek")
            .description("Look in the direction of the wall when hiding behind it.")
            .defaultValue(true)
            .visible(useWalls::get)
            .build()
    );

    private final Setting<Double> wallCheckDistance = sgWall.add(new DoubleSetting.Builder()
            .name("wall-check-distance")
            .description("The distance to check for walls.")
            .defaultValue(4)
            .min(1)
            .sliderMax(8)
            .visible(useWalls::get)
            .build()
    );

    // Weapon control settings
    private final Setting<Boolean> bowPause = sgWeapons.add(new BoolSetting.Builder()
            .name("bow-pause")
            .description("Pauses spinning only when the bow is fully charged and ready to fire.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoAim = sgWeapons.add(new BoolSetting.Builder()
            .name("auto-aim")
            .description("Automatically aims at the nearest target when shooting.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> aimDelay = sgWeapons.add(new IntSetting.Builder()
            .name("aim-delay")
            .description("Delay in ticks before the shot is released after aiming.")
            .defaultValue(2)
            .min(1)
            .sliderMax(10)
            .visible(autoAim::get)
            .build()
    );

    private final Setting<Boolean> wallCheck = sgWeapons.add(new BoolSetting.Builder()
            .name("wall-check")
            .description("Only shoots if there's no wall between you and the target.")
            .defaultValue(true)
            .visible(autoAim::get)
            .build()
    );

    private float yaw = 0;
    private float pitch = 0;
    private final Random random = new Random();
    private int jitterTicks = 0;
    private boolean jitterDirection = false;
    private PlayerEntity target = null;
    private int pauseTicks = 0;
    private float lastNormalYaw = 0;
    private float lastAimPitch = 30; // Default aim pitch
    private int bowPullTicks = 0;
    private boolean bowFullyCharged = false;
    private boolean shouldFireBow = false;
    private int aimTicks = 0;

    public AntiAim() {
        super(burlaos.CATEGORY, "anti-aim", "gamesense moment");
    }

    @Override
    public void onActivate() {
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        jitterTicks = 0;
        pauseTicks = 0;
        lastNormalYaw = yaw;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        
        // Skip if KillAura is active to avoid conflicts
        if (Modules.get().get(KillAura.class).isActive()) return;

        // Check for charged crossbow and disable rotations if found
        if (mc.player.getMainHandStack().getItem() == Items.CROSSBOW && 
            CrossbowItem.isCharged(mc.player.getMainHandStack())) {
            // When holding charged crossbow, disable rotations like when KillAura is active
            return;
        }

        // Handle bow shooting logic
        handleBowLogic();
        
        // Handle aim delay and fire for bow
        if (aimTicks > 0) {
            // If this is the last tick before firing, pause spinning and aim
            if (aimTicks == 1 && shouldFireBow && bowPause.get()) {
                pauseTicks = 2; // Pause for this tick and the tick after release
            }
            
            aimTicks--;
            
            // Only aim at target during the final tick(s) before shooting
            if (aimTicks <= 1 || pauseTicks > 0) {
                // Continue to aim at target while waiting
                Rotations.rotate(lastNormalYaw, lastAimPitch);
            } else {
                // Continue normal anti-aim while waiting for the last moment
                findTarget();
                calculateRotations();
                applyRotations();
                return;
            }
            
            if (aimTicks == 0 && shouldFireBow) {
                // Before releasing, do one final wall check
                if (wallCheck.get() && target != null && !hasLineOfSight(target)) {
                    // Target not visible, don't shoot yet
                    aimTicks = 5; // Try again in 5 ticks
                    return;
                }
                
                // Release the bow
                mc.options.useKey.setPressed(false);
                shouldFireBow = false;
                // Continue aiming for 1 more tick after release for accuracy
                pauseTicks = 1;
            }
            return;
        }

        // Update pause timer
        if (pauseTicks > 0) {
            pauseTicks--;
            // While paused, keep aiming at target
            Rotations.rotate(lastNormalYaw, lastAimPitch);
            return;
        }

        findTarget();
        calculateRotations();
        applyRotations();
    }

    private boolean hasLineOfSight(PlayerEntity target) {
        if (mc.player == null || mc.world == null || target == null) return false;
        
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getEyePos();
        
        // Check if there's a clear line of sight to the target
        return mc.world.raycast(new RaycastContext(
            eyePos,
            targetPos,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        )).getType() == HitResult.Type.MISS;
    }

    private void handleBowLogic() {
        if (!bowPause.get() && !autoAim.get()) return;
        
        boolean isPullingBow = mc.player.getMainHandStack().getItem() == Items.BOW && 
                mc.player.isUsingItem();
        
        // Track bow pull
        if (isPullingBow) {
            bowPullTicks = mc.player.getItemUseTime();
            
            // Check if bow is fully charged (20 ticks is full charge)
            boolean isFullyCharged = bowPullTicks >= 20;
            
            // If bow just became fully charged
            if (isFullyCharged && !bowFullyCharged) {
                bowFullyCharged = true;
                if (autoAim.get()) {
                    // Find a target
                    findTarget();
                    
                    // If we have a target and can see it (if wall check is enabled)
                    if (target != null && (!wallCheck.get() || hasLineOfSight(target))) {
                        // Calculate aim but don't pause yet - will pause right before shooting
                        calculateAndSetAim();
                        aimTicks = aimDelay.get(); // Start the aim delay countdown
                        shouldFireBow = true; // Schedule bow release
                        
                        // Only pause for the final moment right before shooting
                        pauseTicks = 1;
                    }
                }
            }
            
            // Continuously check while bow is pulled in case we peek from behind a wall
            if (bowFullyCharged && autoAim.get() && aimTicks <= 0 && !shouldFireBow) {
                // Find a target
                findTarget();
                
                // If we have a target and can now see it
                if (target != null && (!wallCheck.get() || hasLineOfSight(target))) {
                    // Set up the shot now that we can see
                    calculateAndSetAim();
                    aimTicks = aimDelay.get();
                    shouldFireBow = true;
                }
            }
            
            // While pulling, continue spinning unless we're at the final moment before shooting
        } else {
            // Reset bow tracking when no longer pulling
            if (bowFullyCharged) {
                bowFullyCharged = false;
            }
            bowPullTicks = 0;
        }
    }

    private void calculateAndSetAim() {
        // First try to use the current target
        if (target != null) {
            Vec3d targetPos = target.getEyePos();
            Vec3d playerPos = mc.player.getEyePos();
            
            // Calculate distance for gravity compensation
            double distance = playerPos.distanceTo(targetPos);
            
            // Calculate base aim
            lastNormalYaw = (float) Math.toDegrees(Math.atan2(
                targetPos.z - playerPos.z,
                targetPos.x - playerPos.x
            )) - 90;
            
            // Calculate pitch with gravity compensation
            // For bow/crossbow, we need to aim higher for distant targets
            double heightCompensation = calculateProjectileCompensation(distance);
            
            // Apply height compensation (aim higher for farther targets)
            Vec3d compensatedTarget = targetPos.add(0, heightCompensation, 0);
            
            // Recalculate pitch with compensation
            Vec3d aimDir = compensatedTarget.subtract(playerPos);
            double horizontalDist = Math.sqrt(aimDir.x * aimDir.x + aimDir.z * aimDir.z);
            lastAimPitch = (float) -Math.toDegrees(Math.atan2(aimDir.y, horizontalDist));
        } else {
            // If no target, find a suitable one
            findTarget();
            if (target != null) {
                calculateAndSetAim(); // Recursively call with the new target
            } else {
                // No target found, use current aim
                lastNormalYaw = mc.player.getYaw();
                lastAimPitch = mc.player.getPitch();
            }
        }
    }

    private double calculateProjectileCompensation(double distance) {
        // Simple gravity compensation
        // Will aim higher for farther targets using a quadratic curve
        if (distance < 10) return 0;
        
        // Simplified compensation for different distances
        if (distance < 20) return 0.2;
        if (distance < 30) return 0.5;
        if (distance < 40) return 1.0;
        if (distance < 50) return 1.8;
        return 2.5; // For very far targets
    }

    private void findTarget() {
        if (!targetBased.get()) {
            target = null;
            return;
        }

        target = mc.world.getPlayers().stream()
                .filter(player -> player != mc.player)
                .filter(player -> !player.isSpectator())
                .filter(player -> player.distanceTo(mc.player) <= targetRange.get())
                .min(Comparator.comparingDouble(player -> player.distanceTo(mc.player)))
                .orElse(null);
    }

    private void calculateRotations() {
        // Base spin
        yaw += speed.get().floatValue();
        if (yaw > 180) yaw -= 360;
        
        // Default to looking down
        pitch = downAngle.get().floatValue();
        
        // Target-based rotation
        Direction wallDirection = null;
        if (target != null) {
            Vec3d targetPos = target.getEyePos();
            Vec3d playerPos = mc.player.getEyePos();
            
            float targetYaw = (float) Math.toDegrees(Math.atan2(
                targetPos.z - playerPos.z,
                targetPos.x - playerPos.x
            )) - 90;
            
            // Use opposite direction to hide head
            yaw = targetYaw + 180;
            
            // Wall detection
            if (useWalls.get()) {
                wallDirection = findWallDirection(targetPos);
                if (wallDirection != null) {
                    switch (wallDirection) {
                        case NORTH -> yaw = 180;
                        case SOUTH -> yaw = 0;
                        case EAST -> yaw = -90;
                        case WEST -> yaw = 90;
                        default -> {} // Handle UP, DOWN and any future directions
                    }
                    
                    // Implement wall peeking - look slightly towards the direction of the wall
                    if (wallPeek.get()) {
                        // Calculate target direction for peeking
                        float wallPeekYaw = targetYaw;
                        float wallDirYaw = switch (wallDirection) {
                            case NORTH -> 180;
                            case SOUTH -> 0;
                            case EAST -> -90;
                            case WEST -> 90;
                            default -> yaw;
                        };
                        
                        // Interpolate between wall direction and target direction for peeking
                        // We want to look mostly in the wall direction but slightly towards target
                        float peekFactor = 0.85f; // 85% wall direction, 15% target direction
                        yaw = MathHelper.lerpAngleDegrees(peekFactor, wallPeekYaw, wallDirYaw);
                        
                        // When peeking, raise head slightly from full down position
                        pitch = MathHelper.lerp(0.3f, downAngle.get().floatValue(), 30);
                    }
                }
            }
        }
        
        // Apply jitter
        if (jitter.get()) {
            jitterTicks++;
            if (jitterTicks >= jitterDelay.get()) {
                jitterTicks = 0;
                jitterDirection = !jitterDirection;
                
                float jitterAmount = (float) (jitterDirection ? 
                        jitterIntensity.get() : -jitterIntensity.get());
                jitterAmount *= random.nextFloat();
                
                yaw += jitterAmount;
                
                // Only add pitch jitter if not peeking behind wall
                if (!(useWalls.get() && wallPeek.get() && wallDirection != null)) {
                    // Add smaller pitch jitter to keep head generally down
                    float pitchJitter = random.nextFloat() * 15 - 5; // -5 to +10 degrees jitter
                    pitch = MathHelper.clamp(downAngle.get().floatValue() + pitchJitter, 
                            downAngle.get().floatValue() - 10, 90);
                }
            }
        }
    }

    private Direction findWallDirection(Vec3d targetPos) {
        if (mc.player == null || mc.world == null) return null;
        
        Direction bestDirection = null;
        double maxDistance = 0;
        
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP || direction == Direction.DOWN) continue;
            
            Vec3d checkPos = mc.player.getPos().add(
                direction.getOffsetX() * wallCheckDistance.get(),
                0,
                direction.getOffsetZ() * wallCheckDistance.get()
            );
            
            // Check if there's a wall between the enemy and this position
            boolean isWall = mc.world.raycast(new RaycastContext(
                targetPos,
                checkPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
            )).getType() != HitResult.Type.MISS;
            
            if (isWall) {
                double distance = mc.player.getPos().distanceTo(checkPos);
                if (distance > maxDistance) {
                    maxDistance = distance;
                    bestDirection = direction;
                }
            }
        }
        
        return bestDirection;
    }

    private void applyRotations() {
        Rotations.rotate(yaw, pitch);
    }
}
