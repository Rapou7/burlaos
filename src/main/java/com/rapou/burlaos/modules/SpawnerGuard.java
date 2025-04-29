package com.rapou.burlaos.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import com.rapou.burlaos.burlaos;

public class SpawnerGuard extends Module {

    public SpawnerGuard() {
        super(burlaos.CATEGORY, "Spawner Guard", "Automatically mines spawners and leaves if a non-friend is detected.");
    }

    private boolean triggered = false;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || triggered) return; //checks that the player and the world is loaded and if it was already triggered

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (Friends.get().isFriend(player)) continue;

            triggered = true;

            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(Blocks.SPAWNER);
            //BaritoneAPI.baritone.getPathingBehavior().isPathing();
            mc.player.sendMessage(Text.of("agua señores"), false);

                new Thread(() -> {
                    while (BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {}
                    }
                    
                    mc.execute(() -> {
                        mc.player.networkHandler.getConnection().disconnect(Text.of("por poco"));
                        toggle(); // disable module after disconnecting
                        triggered = false;
                    });
                }).start();
                
                return;
        }
    }
}