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
    // New player starting balance
    public static final ModConfigSpec.DoubleValue STARTING_AMOUNT;
    // Enable GUI Shop
    public static ModConfigSpec.BooleanValue ENABLE_GUI_SHOP;
    // Hard ceiling on a single shop transaction
    public static ModConfigSpec.IntValue MAX_TRANSACTION_QUANTITY;
    // Enable Auction House
    public static ModConfigSpec.BooleanValue ENABLE_AUCTION_HOUSE;
    // Max number of active listings one player may hold at once
    public static ModConfigSpec.IntValue MAX_AUCTION_QUANTITY;
    // Hard ceiling on the item count of a single listing
    public static ModConfigSpec.IntValue MAX_AUCTION_LISTING_SIZE;

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

        MAX_TRANSACTION_QUANTITY = builder
                .comment("Upper limit for a single shop transaction.",
                        "The quantity picker also caps itself at what the player can actually",
                        "hold (when buying) or already holds (when selling)",
                        "Lower it to restrict bulk trading.",
                        "2304 = a full 36-slot inventory of a 64-stack item.")
                .defineInRange("maxTransactionQuantity", 2304, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.push("auction house");

        ENABLE_AUCTION_HOUSE = builder
                .comment("Enable the player Auction House (/ah command)")
                .define("enableAuctionHouse", true);

        MAX_AUCTION_QUANTITY = builder
                .comment("Maximum number of active listings a single player may have at once.",
                        "A player at this cap must let a listing sell or delist one before posting another.")
                .defineInRange("maxAuctionQuantity", 10, 1, Integer.MAX_VALUE);

        MAX_AUCTION_LISTING_SIZE = builder
                .comment("Upper limit on the item count of a single listing.",
                        "/ah sell gathers up to this many of the held item from the player's inventory.")
                .defineInRange("maxAuctionListingSize", 2304, 1, Integer.MAX_VALUE);

        builder.pop();

        SPEC = builder.build();
    }

    private Config() {} // utility class
}
