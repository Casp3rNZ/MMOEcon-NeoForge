package com.casp3rnz.mmoecon.balance.gui;

import com.casp3rnz.mmoecon.balance.BalanceAdministration;
import com.casp3rnz.mmoecon.balance.BalanceTargets;
import com.casp3rnz.mmoecon.core.Messages;
import com.casp3rnz.mmoecon.core.Money;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.Arrays;

/** Vanilla six-row chest UI; all account data and actions remain on the server. */
public final class BalanceAdminMenu extends AbstractContainerMenu {

    private static final int PAGE_SIZE = 45;
    private static final int BACK = 45;
    private static final int PREVIOUS = 48;
    private static final int INFO = 49;
    private static final int NEXT = 50;
    private static final int REFRESH = 53;
    private static final int ACCEPT = 29;
    private static final int CANCEL = 33;
    private static final int[] AMOUNT_SLOTS = { 10, 11, 12, 14, 15, 16 };
    private static final long[] AMOUNT_DELTAS = { -10000L, -100L, -1L, 1L, 100L, 10000L };

    private final SimpleContainer display = new SimpleContainer(54);
    private final ServerPlayer player;
    private final PlayerBalanceAdminSession session;

    public BalanceAdminMenu(int syncId, Inventory inventory, PlayerBalanceAdminSession session) {
        super(MenuType.GENERIC_9x6, syncId);
        this.player = (ServerPlayer) inventory.player;
        this.session = session;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(display, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 198));
        }
        refreshAccounts();
        populate();
    }

    @Override
    public boolean stillValid(Player p) {
        return p == player && BalanceAdminOpener.allowed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player p) {
        if (!stillValid(p)) {
            player.closeContainer();
            return;
        }
        // Ignore shift-click, hotbar swaps, drag, drops, and all inventory movement.
        if (type != ClickType.PICKUP || button != 0 || slot < 0 || slot >= 54) return;
        if (slot == BACK) {
            if (session.view == BalanceAdminViews.ACCOUNTS) {
                player.closeContainer();
                return;
            }
            session.view = session.view == BalanceAdminViews.CONFIRM
                    ? BalanceAdminViews.EDIT : BalanceAdminViews.ACCOUNTS;
            if (session.view == BalanceAdminViews.ACCOUNTS) refreshAccounts();
        } else {
            switch (session.view) {
                case ACCOUNTS -> clickAccount(slot);
                case EDIT -> clickEdit(slot);
                case CONFIRM -> clickConfirm(slot);
            }
        }
        populate();
    }

    private void clickAccount(int slot) {
        if (slot == PREVIOUS && session.page > 0) session.page--;
        else if (slot == NEXT && (session.page + 1) * PAGE_SIZE < session.accounts.size()) session.page++;
        else if (slot == REFRESH) refreshAccounts();
        else if (slot < PAGE_SIZE) {
            int index = session.page * PAGE_SIZE + slot;
            if (index >= session.accounts.size()) return;
            session.target = session.accounts.get(index).uuid();
            BalanceAdministration.Result result = current();
            if (!result.success()) { fail(result); return; }
            session.operation = BalanceAdministration.Operation.SET;
            session.amount = Math.max(0L, result.balance());
            session.view = BalanceAdminViews.EDIT;
        }
    }

    private void clickEdit(int slot) {
        for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
            if (slot == AMOUNT_SLOTS[i]) {
                try {
                    session.amount = Math.max(0L, Math.addExact(session.amount, AMOUNT_DELTAS[i]));
                } catch (ArithmeticException e) {
                    player.sendSystemMessage(Messages.error("The amount would exceed the currency limit."));
                }
                return;
            }
        }
        if (slot == 18) session.amount = 0L;
        else if (slot == 20) selectOperation(BalanceAdministration.Operation.SET);
        else if (slot == 22) selectOperation(BalanceAdministration.Operation.ADD);
        else if (slot == 24) selectOperation(BalanceAdministration.Operation.SUBTRACT);
        else if (slot == 31) {
            BalanceAdministration.Result result = current();
            if (!result.success()) { fail(result); return; }
            BalanceAdministration.Result preview = BalanceAdministration.calculate(
                    result.balance(), session.operation, session.amount);
            if (!preview.success()) { fail(preview); return; }
            session.confirmationBalance = result.balance();
            session.view = BalanceAdminViews.CONFIRM;
        }
    }

    private void selectOperation(BalanceAdministration.Operation operation) {
        if (session.operation == operation) return;
        session.operation = operation;
        BalanceAdministration.Result result = current();
        session.amount = operation == BalanceAdministration.Operation.SET && result.success()
                ? Math.max(0L, result.balance()) : 0L;
    }

    private void clickConfirm(int slot) {
        if (slot == CANCEL) { session.view = BalanceAdminViews.EDIT; return; }
        if (slot != ACCEPT) return;
        BalanceAdministration.Result current = current();
        if (!current.success()) { fail(current); session.view = BalanceAdminViews.EDIT; return; }
        if (current.balance() != session.confirmationBalance) {
            player.sendSystemMessage(Messages.error("The balance changed. Review it and confirm again."));
            session.view = BalanceAdminViews.EDIT;
            return;
        }
        String actor = player.getGameProfile().getName() + " (" + player.getUUID() + ")";
        BalanceAdministration.Result result = BalanceAdministration.execute(
                player.getServer(), actor, session.target, session.operation, session.amount);
        if (result.success()) {
            player.sendSystemMessage(Messages.success(BalanceTargets.describe(player.getServer(), session.target).name()
                    + " balance: $" + Money.formatExact(result.previousBalance())
                    + " -> $" + Money.formatExact(result.balance()) + "."));
        } else fail(result);
        session.view = BalanceAdminViews.EDIT;
    }

    private BalanceAdministration.Result current() {
        return BalanceAdministration.get(player.getServer(), session.target);
    }

    private void fail(BalanceAdministration.Result result) {
        player.sendSystemMessage(Messages.error(BalanceAdministration.failureMessage(result.status())));
    }

    private void refreshAccounts() {
        session.accounts = BalanceTargets.accounts(player.getServer());
        session.page = Math.min(session.page, Math.max(0, (session.accounts.size() - 1) / PAGE_SIZE));
    }

    private void populate() {
        display.clearContent();
        switch (session.view) {
            case ACCOUNTS -> populateAccounts();
            case EDIT -> populateEdit();
            case CONFIRM -> populateConfirm();
        }
        display.setItem(BACK, icon(Items.BARRIER,
                session.view == BalanceAdminViews.ACCOUNTS ? "Close" : "Back"));
        broadcastChanges();
    }

    private void populateAccounts() {
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = session.page * PAGE_SIZE + slot;
            if (index >= session.accounts.size()) break;
            BalanceTargets.Target target = session.accounts.get(index);
            BalanceAdministration.Result result = BalanceAdministration.get(player.getServer(), target.uuid());
            display.setItem(slot, icon(Items.PAPER, target.name(), target.uuid().toString(),
                    result.success() ? "Balance: $" + Money.formatExact(result.balance()) : "Account unavailable",
                    "Click to manage"));
        }
        if (session.accounts.isEmpty()) display.setItem(22, icon(Items.COBWEB, "No stored accounts"));
        if (session.page > 0) display.setItem(PREVIOUS, icon(Items.ARROW, "Previous page"));
        if ((session.page + 1) * PAGE_SIZE < session.accounts.size()) {
            display.setItem(NEXT, icon(Items.ARROW, "Next page"));
        }
        display.setItem(INFO, icon(Items.BOOK, "Accounts", "Page " + (session.page + 1),
                "Stored accounts: " + session.accounts.size()));
        display.setItem(REFRESH, icon(Items.CLOCK, "Refresh accounts"));
    }

    private void populateEdit() {
        populateTarget();
        for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
            long delta = AMOUNT_DELTAS[i];
            display.setItem(AMOUNT_SLOTS[i], icon(delta < 0 ? Items.RED_STAINED_GLASS_PANE
                    : Items.LIME_STAINED_GLASS_PANE,
                    (delta < 0 ? "-" : "+") + "$" + Money.formatExact(Math.abs(delta))));
        }
        display.setItem(18, icon(Items.BUCKET, "Reset amount to $0.00"));
        display.setItem(20, operationIcon(BalanceAdministration.Operation.SET));
        display.setItem(22, operationIcon(BalanceAdministration.Operation.ADD));
        display.setItem(24, operationIcon(BalanceAdministration.Operation.SUBTRACT));
        display.setItem(31, icon(Items.EMERALD, "Review change",
                "Operation: " + session.operation, "Amount: $" + Money.formatExact(session.amount)));
        display.setItem(INFO, icon(Items.BOOK, "Exact amounts",
                "Buttons adjust the amount; review before applying.",
                "Commands also accept exact dollar amounts."));
    }

    private ItemStack operationIcon(BalanceAdministration.Operation operation) {
        return icon(session.operation == operation ? Items.LIME_WOOL : Items.GRAY_WOOL,
                operation.toString(), session.operation == operation ? "Selected" : "Click to select");
    }

    private void populateTarget() {
        BalanceTargets.Target target = BalanceTargets.describe(player.getServer(), session.target);
        BalanceAdministration.Result result = current();
        display.setItem(13, icon(Items.PAPER, target.name(), target.uuid().toString(),
                result.success() ? "Current balance: $" + Money.formatExact(result.balance()) : "Account unavailable"));
    }

    private void populateConfirm() {
        populateTarget();
        BalanceAdministration.Result preview = BalanceAdministration.calculate(
                session.confirmationBalance, session.operation, session.amount);
        display.setItem(22, icon(Items.GOLD_INGOT, "Confirm " + session.operation,
                "Amount: $" + Money.formatExact(session.amount),
                "Before: $" + Money.formatExact(session.confirmationBalance),
                "After: $" + Money.formatExact(preview.balance())));
        display.setItem(ACCEPT, icon(Items.LIME_WOOL, "Apply change"));
        display.setItem(CANCEL, icon(Items.RED_WOOL, "Cancel"));
    }

    private static ItemStack icon(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
                .map(line -> (Component) Component.literal(line).withStyle(s -> s.withItalic(false))).toList()));
        return stack;
    }
}
