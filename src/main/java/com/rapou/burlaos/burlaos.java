package com.rapou.burlaos;

import com.rapou.burlaos.gui.hud.PlayerRadarPlus;
import com.rapou.burlaos.gui.hud.TotemIndicator;
import com.rapou.burlaos.modules.SpawnerGuard;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class burlaos extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("burlaos");
    public static final HudGroup HUD_GROUP = new HudGroup("burlaos");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Addon Template");

        // Modules
        Modules.get().add(new SpawnerGuard());
        
        // HUD
        Hud.get().register(TotemIndicator.INFO);
        Hud.get().register(PlayerRadarPlus.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.rapou.burlaos";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
