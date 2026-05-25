package com.mmoecon.casp3rnz;


import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Logic for /sell hand and /sell inv.
 * Called from EconomyCommands — not a command class itself, just the implementations.
 */
public final class SellCommand {

    static int sellHand(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("You are not holding any item."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        ShopItemManager.ShopItem shopItem = ShopItemManager.findItem(itemId);

        if (shopItem == null || !shopItem.canSell()) {
            player.sendSystemMessage(Component.literal("This item cannot be sold."));
            return 0;
        }

        int qty   = held.getCount();
        float total = shopItem.sellPrice() * qty;

        player.getInventory().removeItem(player.getInventory().selected, qty);
        PlayerBalanceManager.addBalance(player.getUUID(), total);

        String itemName = held.getHoverName().getString();
        player.sendSystemMessage(Component.literal(
                "Sold " + qty + "x " + itemName + " for $" + ShopMenu.formatMoney(total)));
        TransactionLogger.log(player.getName().getString() + " sold " + qty + " " + itemName
                + " for $" + ShopMenu.formatMoney(total));

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
        return 1;
    }

    static int sellInventory(ServerPlayer player) {
        float totalEarned = 0f;
        int   totalSold   = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // Skip armour and offhand slots
            if (isArmorOrOffhand(player, stack)) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            ShopItemManager.ShopItem shopItem = ShopItemManager.findItem(itemId);
            if (shopItem == null || !shopItem.canSell()) continue;

            int qty       = stack.getCount();
            float earned  = shopItem.sellPrice() * qty;
            totalEarned  += earned;
            totalSold    += qty;

            String itemName = stack.getHoverName().getString();
            player.getInventory().setItem(i, ItemStack.EMPTY);
            TransactionLogger.log(player.getName().getString() + " sold " + qty + " " + itemName
                    + " for $" + ShopMenu.formatMoney(earned));
        }

        if (totalSold > 0) {
            final float earned = totalEarned;
            PlayerBalanceManager.addBalance(player.getUUID(), totalEarned);
            player.sendSystemMessage(Component.literal(
                    "Sold " + totalSold + " items for $" + ShopMenu.formatMoney(totalEarned) + " total."));
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
        } else {
            player.sendSystemMessage(Component.literal("No sellable items found in your inventory."));
        }

        return 1;
    }

    private static boolean isArmorOrOffhand(ServerPlayer player, ItemStack stack) {
        for (ItemStack armour : player.getInventory().armor) {
            if (armour == stack) return true;
        }
        return player.getInventory().offhand.get(0) == stack;
    }

    private SellCommand() {}
}
