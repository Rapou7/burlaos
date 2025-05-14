package com.rapou.burlaos.gui.hud;

import com.rapou.burlaos.burlaos;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TotemIndicator extends HudElement {
    public static final HudElementInfo<TotemIndicator> INFO = new HudElementInfo<>(burlaos.HUD_GROUP, "totem-indicator", "Displays whether you have a totem in your hand or offhand.", TotemIndicator::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("The scale of the indicator.")
            .defaultValue(1.5)
            .min(0.1)
            .sliderRange(0.1, 5)
            .onChanged(aDouble -> calculateSize())
            .build()
    );

    private final Setting<SettingColor> safeColor = sgGeneral.add(new ColorSetting.Builder()
            .name("safe-color")
            .description("Color when you have a totem.")
            .defaultValue(new SettingColor(25, 225, 25))
            .build()
    );

    private final Setting<SettingColor> dangerColor = sgGeneral.add(new ColorSetting.Builder()
            .name("danger-color")
            .description("Color when you don't have a totem.")
            .defaultValue(new SettingColor(225, 25, 25))
            .build()
    );

    private final Setting<BackgroundMode> backgroundMode = sgGeneral.add(new EnumSetting.Builder<BackgroundMode>()
            .name("background-mode")
            .description("How to render the background.")
            .defaultValue(BackgroundMode.Static)
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Color of background when using static background.")
            .defaultValue(new SettingColor(0, 0, 0, 75))
            .visible(() -> backgroundMode.get() == BackgroundMode.Static)
            .build()
    );

    private final Setting<Integer> dynamicBackgroundOpacity = sgGeneral.add(new IntSetting.Builder()
            .name("dynamic-background-opacity")
            .description("Opacity of background when using dynamic coloring.")
            .defaultValue(75)
            .min(0)
            .max(255)
            .sliderRange(0, 255)
            .visible(() -> backgroundMode.get() == BackgroundMode.Dynamic)
            .build()
    );

    public TotemIndicator() {
        super(INFO);
    }

    private void calculateSize() {
        if (mc.textRenderer == null) return;
        String measureText = "DANGER"; // Longer text to ensure enough space
        // Use fixed approximations for initial size
        double textWidth = 6 * measureText.length();
        double textHeight = 10;
        setSize(textWidth * scale.get() + 8, textHeight * scale.get() + 8);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;

        boolean hasTotem = hasTotem();
        String text = hasTotem ? "SAFE" : "DANGER";
        Color textColor = hasTotem ? safeColor.get() : dangerColor.get();

        // Calculate text dimensions for this render
        double textWidth = renderer.textWidth(text);
        double textHeight = renderer.textHeight();
        
        // Set size based on current renderer's text metrics
        double width = textWidth * scale.get() + 8;
        double height = textHeight * scale.get() + 8;
        setSize(width, height);

        double x = this.x;
        double y = this.y;

        // Draw background
        if (backgroundMode.get() != BackgroundMode.None) {
            Color bgColor;
            if (backgroundMode.get() == BackgroundMode.Dynamic) {
                // Create a new color with same RGB but custom opacity
                bgColor = new Color(
                    textColor.r,
                    textColor.g,
                    textColor.b,
                    dynamicBackgroundOpacity.get()
                );
            } else {
                bgColor = backgroundColor.get();
            }
            renderer.quad(x, y, width, height, bgColor);
        }

        // Center text with proper scaling
        double textX = x + (width - textWidth * scale.get()) / 2;
        double textY = y + (height - textHeight * scale.get()) / 2;

        renderer.text(text, textX, textY, textColor, false, scale.get());
    }

    private boolean hasTotem() {
        Item mainHandItem = mc.player.getMainHandStack().getItem();
        Item offHandItem = mc.player.getOffHandStack().getItem();
        
        return mainHandItem == Items.TOTEM_OF_UNDYING || offHandItem == Items.TOTEM_OF_UNDYING;
    }

    public enum BackgroundMode {
        None("None"),
        Static("Static"),
        Dynamic("Dynamic");

        private final String title;

        BackgroundMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
} 