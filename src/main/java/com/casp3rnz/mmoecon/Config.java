package com.casp3rnz.mmoecon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * TOML-based server config using NeoForge's ModConfigSpec.
 * Values are registered in the static initialiser and wired up in MMOEcon's
 * construstor via modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC).
 *
 * NeoForge writes the file to <server>/config/mmoecon-server.toml automatically.
 * Read e.g. Config.ENABLE_PLAYTIME_REWARDS.get().
 */


public final class Config {

    // Spec (Registered in MMOEcon Constructor)
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_PLAYTIME_REWARDS;
    public static final ModConfigSpec.DoubleValue PLAYTIME_REWARD;
    // Interval in ticks (36000 = 30 min)
    public static final ModConfigSpec.LongValue PLAYTIME_INTERVAL;

    // Kill Rewards (todo in later update)
    //public static final ModConfigSpec.BooleanValue ENABLE_KILL_REWARDS_PVE;
    //public static final ModConfigSpec.DoubleValue KILL_REWARD_PVE;
    //public static final ModConfigSpec.BooleanValue ENABLE_KILL_REWARDS_PVP;
    //public static final ModConfigSpec.DoubleValue KILL_REWARD_PVP;

    // New player starting balance
    public static final ModConfigSpec.DoubleValue STARTING_AMOUNT;

    // Enable GUI Shop
    public static ModConfigSpec.BooleanValue ENABLE_GUI_SHOP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("MMOEcon server Config").push("economy");

        ENABLE_PLAYTIME_REWARDS = builder
                .comment("Awards money to players on a timed interval while they are online.")
                .define("enablePlaytimeRewards", true);

        PLAYTIME_REWARD = builder
                .comment("Amount of money awarded per playtime interval.")
                .defineInRange("playtimeReward", 200.0, 0.0, Double.MAX_VALUE);

        PLAYTIME_INTERVAL = builder
                .comment("Ticks between playtime reward payouts.")
                .defineInRange("playtimeInterval", 36000L, 1L, Long.MAX_VALUE);

        STARTING_AMOUNT = builder
                .comment("Starting balance for new players.")
                .defineInRange("startingAmount", 1000.0, 0.0, Double.MAX_VALUE);

        builder.pop();

        builder.push("shop");

        ENABLE_GUI_SHOP = builder
                .comment("Enable the chest-based GUI shop (/shop command).")
                .define("enableGUIShop", true);

        builder.pop();

        SPEC = builder.build();
    }

    private Config() {} // utility class
}
