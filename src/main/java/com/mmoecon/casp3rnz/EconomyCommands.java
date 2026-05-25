package com.mmoecon.casp3rnz;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;

/**
 * Registers all economy commands on the NeoForge event bus.
 *
 * Registered in MMOEcon constructor:
 *   NeoForge.EVENT_BUS.register(EconomyCommands.class);
 *
 * Commands:
 *   /bal            — show own balance
 *   /money          — alias for /bal
 *   /bal top        — top 10 richest players
 *   /pay <name> <amount>
 *   /shop           — open shop GUI
 *   /shop reload    — reload shop JSON config
 *   /sell hand      — sell held item
 *   /sell inv       — sell all sellable items in inventory
 */
public final class EconomyCommands {

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerNames(), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        // /bal and /money
        dispatcher.register(Commands.literal("bal").executes(ctx -> showBalance(ctx.getSource())));
        dispatcher.register(Commands.literal("money").executes(ctx -> showBalance(ctx.getSource())));

        // /bal top
        dispatcher.register(Commands.literal("bal")
                .then(Commands.literal("top").executes(ctx -> showBalTop(ctx.getSource()))));

        // /pay <player> <amount>
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests(ONLINE_PLAYERS)
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.01f))
                                .executes(ctx -> {
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    float amount = FloatArgumentType.getFloat(ctx, "amount");
                                    return executePay(ctx.getSource(), targetName, amount);
                                }))));

        // /shop
        dispatcher.register(Commands.literal("shop")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ShopOpener.open(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2)) // op-only
                        .executes(ctx -> {
                            ShopItemManager.reload();
                            ctx.getSource().sendSuccess(() -> Component.literal("§aShop config reloaded."), true);
                            return Command.SINGLE_SUCCESS;
                        })));

        // /sell hand | inv
        dispatcher.register(Commands.literal("sell")
                .then(Commands.literal("hand").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return SellCommand.sellHand(player);
                }))
                .then(Commands.literal("inv").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return SellCommand.sellInventory(player);
                })));
    }

    // ── Command implementations ───────────────────────────────────────────────

    private static int showBalance(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        float balance = PlayerBalanceManager.getBalance(player.getUUID());
        src.sendSuccess(() -> Component.literal("Your balance: $" + ShopMenu.formatMoney(balance)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showBalTop(CommandSourceStack src) {
        List<Map.Entry<UUID, Float>> sorted = new ArrayList<>(PlayerBalanceManager.getBalances().entrySet());
        sorted.sort(Map.Entry.<UUID, Float>comparingByValue().reversed());

        src.sendSuccess(() -> Component.literal("§6§lWealthiest players:"), false);
        MinecraftServer server = src.getServer();

        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<UUID, Float> entry = sorted.get(i);
            Optional<GameProfile> profile = server.getProfileCache().get(entry.getKey());
            String name = profile.map(GameProfile::getName).orElse("unknown");
            final int rank = i + 1;
            final String display = rank + ". " + name + ": $" + ShopMenu.formatMoney(entry.getValue());
            src.sendSuccess(() -> Component.literal(display), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executePay(CommandSourceStack src, String targetName, float amount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer sender = src.getPlayerOrException();
        ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            src.sendFailure(Component.literal("Player '" + targetName + "' is not online."));
            return 0;
        }
        if (target.equals(sender)) {
            src.sendFailure(Component.literal("You can't pay yourself."));
            return 0;
        }
        if (!PlayerBalanceManager.hasFunds(sender.getUUID(), amount)) {
            src.sendFailure(Component.literal("You don't have enough money."));
            return 0;
        }

        PlayerBalanceManager.subtractBalance(sender.getUUID(), amount);
        PlayerBalanceManager.addBalance(target.getUUID(), amount);

        src.sendSuccess(() -> Component.literal("Paid $" + ShopMenu.formatMoney(amount) + " to " + targetName + "."), false);
        target.sendSystemMessage(Component.literal(sender.getName().getString() + " paid you $" + ShopMenu.formatMoney(amount) + "."));

        return Command.SINGLE_SUCCESS;
    }

    private EconomyCommands() {}
}
