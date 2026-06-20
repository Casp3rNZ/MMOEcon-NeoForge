package com.casp3rnz.mmoecon;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Manages per-player balances, persisted to config/mmoecon/balances.dat.
 */

public class PlayerBalanceManager {

    private static final Path BALANCE_FILE =
            FMLPaths.CONFIGDIR.get().resolve("mmoecon/balances.dat");

    private static final Map<UUID, Float> balances = new ConcurrentHashMap<>();

    private static volatile boolean dirty = false;
    private static final long FLUSH_INTERVAL_TICKS = 1200L; // ~60s

    // Init
    private static void load() {
        balances.clear();
        if(!Files.exists(BALANCE_FILE)) {
            MMOEcon.LOGGER.info("No balance file found, starting fresh.");
            return;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(BALANCE_FILE.toFile()))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                UUID uuid    = UUID.fromString(in.readUTF());
                float balance = in.readFloat();
                balances.put(uuid, balance);
            }
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to load balance file from {}: {}", BALANCE_FILE, e.getMessage());
        }
    }

    private static void saveBalances() {
        try {
            Files.createDirectories(BALANCE_FILE.getParent());
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(BALANCE_FILE.toFile()))) {
                out.writeInt(balances.size());
                for (Map.Entry<UUID, Float> entry : balances.entrySet()) {
                    out.writeUTF(entry.getKey().toString());
                    out.writeFloat(entry.getValue());
                }
            }
            dirty = false;
        } catch (IOException e) {
            MMOEcon.LOGGER.error("Failed to save balance file to {}: {}", BALANCE_FILE, e.getMessage());
        }
    }

    // NeoForge Event Listeners
    /**
     * SERVER configs are guaranteed loaded before this event fires, so this is
     * the earliest safe point to load balances and read ConfigManager values.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        UUID uuid = event.getEntity().getUUID();
        if (!balances.containsKey(uuid)) {
            // Safe to call here — we are post-server-start, config is loaded.
            float startAmount = Config.STARTING_AMOUNT.get().floatValue();
            balances.put(uuid, startAmount);
            MMOEcon.LOGGER.info("New player {}: starting balance ${}", uuid, startAmount);
            dirty = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (dirty && server.getTickCount() % FLUSH_INTERVAL_TICKS == 0) {
            saveBalances();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (dirty) saveBalances();
    }

    // Public Balance API

    public static float getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, Config.STARTING_AMOUNT.get().floatValue());
    }

    public static void setBalance(UUID uuid, float amount) {
        balances.put(uuid, Math.max(0f, amount));
        dirty = true;
    }

    public static void addBalance(UUID uuid, float amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    /**
     * Subtract amount from balance. Does NOT deduct if the player lacks
     * sufficient funds — callers should check first with hasFunds.
     */
    public static void subtractBalance(UUID uuid, float amount) {
        if(!hasFunds(uuid, amount)) return;
        setBalance(uuid, getBalance(uuid) - amount);
    }

    public static boolean hasFunds(UUID uuid, float amount) {
        return getBalance(uuid) >= amount;
    }

    // Read-only view of balance map (for baltop)
    public static Map<UUID, Float> getBalances() { return Collections.unmodifiableMap(balances); }


}
