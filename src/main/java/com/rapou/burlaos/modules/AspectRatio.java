package com.rapou.burlaos.modules;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import com.rapou.burlaos.burlaos;

public class AspectRatio extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> ratioSetting = sgGeneral.add(new IntSetting.Builder()
            .name("ratio")
            .description("The aspect ratio percentage.")
            .defaultValue(100)
            .range(10, 300)
            .sliderRange(50, 300)
            .build()
    );

    public AspectRatio() {
        super(burlaos.CATEGORY, "aspect-ratio", "makes the game wide");
    }

    public float getRatioMultiplier() {
        return ratioSetting.get() / 100.0f;
    }
} 