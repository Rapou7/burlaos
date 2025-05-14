package com.rapou.burlaos.gui.hud;

import com.rapou.burlaos.burlaos;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PlayerRadarPlus extends HudElement {
    public static final HudElementInfo<PlayerRadarPlus> INFO = new HudElementInfo<>(burlaos.HUD_GROUP, "player-radar+", "Draws a radar showing nearby players as dots.", PlayerRadarPlus::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Color of background.")
            .defaultValue(new SettingColor(0, 0, 0, 64))
            .build()
    );

    private final Setting<SettingColor> playerColor = sgGeneral.add(new ColorSetting.Builder()
            .name("player-color")
            .description("Color of player dots.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(1)
            .min(1)
            .sliderRange(0.01, 5)
            .onChanged(aDouble -> calculateSize())
            .build()
    );

    private final Setting<Double> zoom = sgGeneral.add(new DoubleSetting.Builder()
        .name("zoom")
        .description("Radar zoom.")
        .defaultValue(1)
        .min(0.01)
        .sliderRange(0.01, 3)
        .build()
    );

    private final Setting<Double> dotSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("dot-size")
        .description("Size of player dots.")
        .defaultValue(2.0)
        .min(1.0)
        .sliderRange(1.0, 5.0)
        .build()
    );

    public PlayerRadarPlus() {
        super(INFO);
        calculateSize();
    }

    public void calculateSize() {
        setSize(200 * scale.get(), 200 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        renderer.post(() -> {
            if (mc.player == null || mc.world == null) return;
            
            double width = getWidth();
            double height = getHeight();
            double centerX = x + width / 2;
            double centerY = y + height / 2;
            
            // Draw background
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x, y, width, height, backgroundColor.get());
            
            // Draw player's position at center as a circle
            drawCircle(centerX, centerY, dotSize.get(), Color.WHITE);
            
            // Get the player's rotation
            float playerYaw = mc.player.getYaw();
            float yawRadians = (float) Math.toRadians(-playerYaw);
            
            // Draw other players
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof PlayerEntity) || entity == mc.player) continue;
                
                PlayerEntity player = (PlayerEntity) entity;
                
                // Get relative position
                double relX = entity.getX() - mc.player.getX();
                double relZ = entity.getZ() - mc.player.getZ();
                
                // Rotate based on player's yaw to lock north to player view
                // Invert X axis calculation to fix left-right orientation
                double rotatedX = -(relX * MathHelper.cos(yawRadians) - relZ * MathHelper.sin(yawRadians));
                // Invert Z axis to fix orientation - players ahead are shown ahead
                double rotatedZ = -(relX * MathHelper.sin(yawRadians) + relZ * MathHelper.cos(yawRadians));
                
                // Calculate position on radar
                double xPos = rotatedX * scale.get() * zoom.get() + width / 2;
                double yPos = rotatedZ * scale.get() * zoom.get() + height / 2;
                
                // Check if player is within radar bounds
                if (xPos < 0 || yPos < 0 || xPos > width || yPos > height) continue;
                
                // Use Meteor's player color utility to get the appropriate color
                Color dotColor = PlayerUtils.getPlayerColor(player, playerColor.get());
                
                // Draw player as a circle
                drawCircle(x + xPos, y + yPos, dotSize.get(), dotColor);
            }
            
            Renderer2D.COLOR.render(null);
        });
    }
    
    // Helper method to draw a circle
    private void drawCircle(double centerX, double centerY, double radius, Color color) {
        int segments = 12; // Number of segments used to draw the circle
        
        // Draw a series of triangles to approximate a circle
        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;
            
            double x1 = centerX + Math.sin(angle1) * radius;
            double y1 = centerY + Math.cos(angle1) * radius;
            double x2 = centerX + Math.sin(angle2) * radius;
            double y2 = centerY + Math.cos(angle2) * radius;
            
            Renderer2D.COLOR.triangle(
                centerX, centerY,
                x1, y1,
                x2, y2,
                color
            );
        }
    }
}
