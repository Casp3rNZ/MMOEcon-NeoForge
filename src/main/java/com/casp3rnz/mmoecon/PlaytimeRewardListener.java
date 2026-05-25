package com.casp3rnz.mmoecon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Awards all online players a money reward every PLAYTIME_INTERVAL ticks.
 */
public final class PlaytimeRewardListener {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.ENABLE_PLAYTIME_REWARDS.get()) return;

        MinecraftServer server = event.getServer();
        long interval = Config.PLAYTIME_INTERVAL.get();

        if (server.getTickCount() % interval != 0) return;

        // Skip the very first tick (tickCount == 0 at world load would fire immediately)
        if (server.getTickCount() == 0) return;

        float reward = Config.PLAYTIME_REWARD.get().floatValue();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerBalanceManager.addBalance(player.getUUID(), reward);
            player.sendSystemMessage(Component.literal(
                    "§a+$" + ShopMenu.formatMoney(reward)
                            + " §7playtime reward!"));
        }
    }

    private PlaytimeRewardListener() {}
}