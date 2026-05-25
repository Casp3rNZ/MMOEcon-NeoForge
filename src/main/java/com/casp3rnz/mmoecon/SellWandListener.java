package com.casp3rnz.mmoecon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Listens for right-click block events and handles the sell wand flow.
 *
 * Supported containers: anything whose BlockEntity implements Container —
 *  * this covers minecraft:chest, minecraft:barrel, minecraft:trapped_chest,
 *  * minecraft:shulker_box, and most modded chests (Quark, etc.) automatically,
 *  * as long as they implement Container.
 *
 * Flow:
 *   1st right-click: scan container, build preview, store PendingSale
 *   2nd right-click on same block within 15s: execute sale
 *   right-click different block, or timeout: clear pending, start fresh
 */

public class SellWandListener {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Server-side only
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ItemStack held = player.getMainHandItem();
        if (!SellWand.isWand(held)) return;
        event.setCanceled(true);

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        // Check if the clicked block is a supported container
        Container container = getContainer(level, pos);
        if (container == null) {
            player.sendSystemMessage(Component.literal("§cThis block is not a supported container."));
            SellWand.clearPending(player.getUUID());
            return;
        }

        UUID uuid = player.getUUID();

        // Second click: confirm
        if (SellWand.hasPending(uuid)) {
            SellWand.PendingSale pending = SellWand.getPending(uuid);

            // Must be the same block — clicking a different chest resets
            if (!pending.blockPos().equals(pos)) {
                SellWand.clearPending(uuid);
                previewContainer(player, container, pos);
                return;
            }

            // Execute the sale
            executeSale(player, container, pending);
            SellWand.clearPending(uuid);
            return;
        }

        // First click: preview
        previewContainer(player, container, pos);
    }

    // Preview
    private static void previewContainer(ServerPlayer player, Container container, BlockPos pos) {
        SaleResult preview = calculateSale(container);

        if (preview.totalItems == 0) {
            player.sendSystemMessage(Component.literal("§cNo sellable items found in this container."));
            return;
        }

        // Store pending sale
        long now = player.serverLevel().getServer().getTickCount();
        SellWand.setPending(player.getUUID(), new SellWand.PendingSale(
                pos, preview.totalEarned, preview.totalItems, now));

        // Send preview message
        player.sendSystemMessage(Component.literal(
                "§eFound §f" + preview.totalItems + " §esellable items worth §a$"
                        + ShopMenu.formatMoney(preview.totalEarned) + "§e."));
        player.sendSystemMessage(Component.literal(
                "§eRight-click the same chest again within §f15 seconds §eto confirm."));

        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    // Execute Sale

    private static void executeSale(ServerPlayer player, Container container, SellWand.PendingSale pending) {
        // Recalculate at execution time in case contents changed between clicks
        SaleResult actual = calculateSale(container);

        if (actual.totalItems == 0) {
            player.sendSystemMessage(Component.literal("§cNo sellable items found — the container may have changed."));
            return;
        }

        // Remove the items and credit the player
        removeItems(container, actual.slots);
        PlayerBalanceManager.addBalance(player.getUUID(), actual.totalEarned);

        player.sendSystemMessage(Component.literal(
                "§aSold §f" + actual.totalItems + " §aitems for §a$"
                        + ShopMenu.formatMoney(actual.totalEarned) + "§a!"));

        TransactionLogger.log(player.getName().getString()
                + " used sell wand at " + pending.blockPos()
                + " — sold " + actual.totalItems + " items for $"
                + ShopMenu.formatMoney(actual.totalEarned));

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    // Helpers

    /**
     * Scans a container and returns a SaleResult with total value and a list
     * of slot indices that contain sellable items.
     * Does NOT modify the container.
     */
    private static SaleResult calculateSale(Container container) {
        float totalEarned = 0f;
        int totalItems = 0;
        List<SlotSale> slots = new ArrayList<>();

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            ShopItemManager.ShopItem shopItem = ShopItemManager.findItem(itemId);
            if (shopItem == null || !shopItem.canSell()) continue;

            int qty = stack.getCount();
            float earned = shopItem.sellPrice() * qty;
            totalEarned += earned;
            totalItems += qty;
            slots.add(new SlotSale(i, qty, earned));
        }

        return new SaleResult(totalEarned, totalItems, slots);
    }

    /**
     * Returns the container at the given position, automatically combining
     * both halves if the block is a large (double) chest or trapped chest.
     * Falls back to the raw BlockEntity inventory for any other container.
     * Returns null if the block has no inventory.
     */
    private static Container getContainer(ServerLevel level, BlockPos pos) {

        BlockEntity be = level.getBlockEntity(pos);
        if(be == null) return null;

        // Handle double chests by checking for a neighbour chest and combining
        if (be instanceof ChestBlockEntity chest) {
            BlockState state = level.getBlockState(pos);

            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction partnerDir = chestType == ChestType.RIGHT
                        ? facing.getCounterClockWise()
                        : facing.getClockWise();

                BlockPos partnerPos = pos.relative(partnerDir);
                BlockState partnerState = level.getBlockState(partnerPos);
                BlockEntity partnerBe = level.getBlockEntity(partnerPos);

                // Verify same block type AND opposite chest half — rules out adjacent unrelated chests
                ChestType partnerType =
                        partnerState.hasProperty(ChestBlock.TYPE)
                                ? partnerState.getValue(ChestBlock.TYPE)
                                : ChestType.SINGLE;

                boolean trulyPaired = partnerBe instanceof ChestBlockEntity
                        && partnerState.getBlock() == state.getBlock()
                        && partnerType != chestType  // opposite halves
                        && partnerType != ChestType.SINGLE;

                if (trulyPaired) {
                    ChestBlockEntity partnerChest = (ChestBlockEntity) partnerBe;
                    if (chestType == ChestType.RIGHT) {
                        return new CompoundContainer(chest, partnerChest);
                    } else {
                        return new CompoundContainer(partnerChest, chest);
                    }
                }
            }

            // If single chest
            return chest;
        }
        return null;
    }

    /** Removes items from the container at the slots identified by calculateSale. */
    private static void removeItems(Container container, List<SlotSale> slots) {
        for (SlotSale slot : slots) {
            container.setItem(slot.slotIndex(), ItemStack.EMPTY);
        }
        container.setChanged();
    }

    // Internal records

    private record SlotSale(int slotIndex, int quantity, float earned) {}

    private record SaleResult(float totalEarned, int totalItems, List<SlotSale> slots) {}

    private SellWandListener() {}

}
