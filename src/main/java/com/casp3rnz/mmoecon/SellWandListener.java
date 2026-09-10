package com.casp3rnz.mmoecon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Listens for right-click block events and handles the sell wand flow.
 *
 * Supported containers: the wand reads inventories through NeoForge's
 * Capabilities.ItemHandler.BLOCK capability, falling back to the vanilla
 * Container interface for blocks that don't expose it.
 *  * The capability path covers drawer/storage mods (Functional Drawers,
 *    Storage Drawers, etc.) and most modern modded storage. It also handles
 *    slots holding more than one stack, which vanilla Container cannot express.
 *  * The Container fallback covers minecraft:chest, minecraft:barrel,
 *    minecraft:trapped_chest, minecraft:shulker_box and older modded chests,
 *    including double-chest pairing.
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

        // Check if the clicked block is a supported container
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) {
            player.sendSystemMessage(Messages.error("That isn't a container you can sell from."));
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
                previewContainer(player, handler, pos);
                return;
            }

            // Execute the sale
            executeSale(player, handler, pending);
            SellWand.clearPending(uuid);
            return;
        }

        // First click: preview
        previewContainer(player, handler, pos);
    }

    // Preview
    private static void previewContainer(ServerPlayer player, IItemHandler handler, BlockPos pos) {
        SaleResult preview = calculateSale(handler);

        if (preview.totalItems == 0) {
            player.sendSystemMessage(Messages.error("Nothing in there can be sold."));
            return;
        }

        // Store pending sale
        long now = player.serverLevel().getServer().getTickCount();
        SellWand.setPending(player.getUUID(), new SellWand.PendingSale(
                pos, preview.totalEarned, preview.totalItems, now));

        // Send preview message. The window is derived from the timeout constant so
        // the two can't drift apart if it's ever retuned.
        long seconds = SellWand.CONFIRM_TIMEOUT_TICKS / 20L;
        player.sendSystemMessage(Messages.body(
                "Found " + Messages.item(preview.totalItems + " sellable items")
                        + " worth " + Messages.money(preview.totalEarned) + "."));
        player.sendSystemMessage(Messages.body(
                "Right-click again within " + Messages.item(seconds + "s") + " to confirm."));

        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    // Execute Sale

    private static void executeSale(ServerPlayer player, IItemHandler handler, SellWand.PendingSale pending) {
        // Recalculate at execution time in case contents changed between clicks
        SaleResult planned = calculateSale(handler);

        if (planned.totalItems == 0) {
            player.sendSystemMessage(Messages.error("The container's contents changed, nothing was sold."));
            return;
        }

        // Pay for what actually came out, not what the scan predicted
        SaleResult actual = removeItems(handler, planned.slots);

        if (actual.totalItems == 0) {
            player.sendSystemMessage(Messages.error("Nothing could be removed from that container."));
            return;
        }

        PlayerBalanceManager.addBalance(player.getUUID(), actual.totalEarned);

        player.sendSystemMessage(Messages.body(
                "Sold " + Messages.item(actual.totalItems + " items")
                        + " for " + Messages.money(actual.totalEarned) + "."));

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
    private static SaleResult calculateSale(IItemHandler handler) {
        long totalEarned = 0L;
        int totalItems = 0;
        List<SlotSale> slots = new ArrayList<>();

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // The sell wand is a custom-NBT blaze rod: price it off its own "sell_wand"
            // entry, never the blaze_rod entry.
            ShopItemManager.ShopItem shopItem = SellWand.isWand(stack)
                    ? ShopItemManager.findSpecial("sell_wand")
                    : ShopItemManager.findItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (shopItem == null || !shopItem.canSell()) continue;

            // Only count what the handler will actually hand over. Drawer mods keep
            // a locked/"protected" stack that can't be extracted, and getStackInSlot
            // reports the full contents including it — pricing off the raw count
            // would pay the player for items the sale can't remove.
            int extractable = handler.extractItem(i, stack.getCount(), true).getCount();
            if (extractable <= 0) continue;

            long unitPrice = shopItem.sellPrice();
            long earned = Money.multiply(unitPrice, extractable);
            totalEarned += earned;
            totalItems += extractable;
            slots.add(new SlotSale(i, extractable, unitPrice, earned));
        }

        return new SaleResult(totalEarned, totalItems, slots);
    }

    /**
     * Resolves the inventory at the given position as an IItemHandler.
     * Order matters: the ItemHandler capability is queried first, because mods
     * that expose both (and drawer mods that expose only the capability) model
     * their real contents there — a vanilla Container view of a drawer would
     * under-report a slot holding thousands of items.
     * Falls back to wrapping the vanilla Container, which keeps double-chest
     * pairing working. Returns null if the block has no inventory at all.
     */
    private static IItemHandler getItemHandler(ServerLevel level, BlockPos pos) {
        // Null face = the block's general/unsided inventory view.
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) return handler;

        Container container = getContainer(level, pos);
        return container == null ? null : new InvWrapper(container);
    }

    /**
     * Returns the vanilla container at the given position, automatically combining
     * both halves if the block is a large (double) chest or trapped chest.
     * Falls back to the raw BlockEntity inventory for any other container.
     * Returns null if the block has no inventory.
     */
    private static Container getContainer(ServerLevel level, BlockPos pos) {

        BlockEntity be = level.getBlockEntity(pos);
        switch (be) {
            case null -> {
                return null;
            }

            // Handle double chests by checking for a neighbour chest and combining
            case ChestBlockEntity chest -> {
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

                return chest;
            }
            case Container container -> {
                return container;
            }
            default -> {
            }
        }

        return null;
    }

    /**
     * Extracts the priced items and returns what was actually removed.
     * Uses extractItem rather than clearing the slot: a drawer slot can hold far
     * more than the priced amount, so emptying it would destroy items the player
     * was never paid for. If a handler hands back less than it promised during the
     * simulation, the shortfall is dropped from the payout instead of trusting the
     * earlier estimate.
     */
    private static SaleResult removeItems(IItemHandler handler, List<SlotSale> slots) {
        long earned = 0L;
        int removed = 0;
        List<SlotSale> actual = new ArrayList<>();

        for (SlotSale slot : slots) {
            ItemStack taken = handler.extractItem(slot.slotIndex(), slot.quantity(), false);
            if (taken.isEmpty()) continue;

            int count = taken.getCount();
            long slotEarned = Money.multiply(slot.unitPrice(), count);

            earned += slotEarned;
            removed += count;
            actual.add(new SlotSale(slot.slotIndex(), count, slot.unitPrice(), slotEarned));
        }

        return new SaleResult(earned, removed, actual);
    }

    // Internal records

    private record SlotSale(int slotIndex, int quantity, long unitPrice, long earned) {}

    private record SaleResult(long totalEarned, int totalItems, List<SlotSale> slots) {}

    private SellWandListener() {}

}
