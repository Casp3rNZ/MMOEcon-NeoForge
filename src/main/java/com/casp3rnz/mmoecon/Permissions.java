package com.casp3rnz.mmoecon;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Central permission definitions for MMOEcon.
 * Every gated command resolves through NeoForge's {@link PermissionAPI}.
 * Nodes (all boolean):
 *   mmoecon.command.bal       — /bal, /money, /bal top      (default: everyone)
 *   mmoecon.command.pay       — /pay                        (default: everyone)
 *   mmoecon.command.shop      — /shop                       (default: everyone)
 *   mmoecon.command.sell      — /sell hand | inv            (default: everyone)
 *   mmoecon.command.ah        — /ah, /auction,/auctionhouse (default: everyone)
 *   mmoecon.admin.reload      — /shop reload                (default: OP, level 2)
 *   mmoecon.admin.sellwand    — /sellwand give              (default: OP, level 2)
 * Registered on the NeoForge game event bus in the MMOEcon constructor:
 *   NeoForge.EVENT_BUS.register(Permissions.class);
 */
public final class Permissions {

    /** Vanilla OP level required by admin commands when no permission mod is present. */
    public static final int OP_LEVEL = 2;

    // Player-facing commands: available to everyone by default.
    public static final PermissionNode<Boolean> BAL = node("command.bal",   defaultTo(0));
    public static final PermissionNode<Boolean> PAY = node("command.pay",   defaultTo(0));
    public static final PermissionNode<Boolean> SHOP = node("command.shop",  defaultTo(0));
    public static final PermissionNode<Boolean> SELL = node("command.sell",  defaultTo(0));
    public static final PermissionNode<Boolean> AH = node("command.ah",    defaultTo(0));

    // Admin commands: OP by default.
    public static final PermissionNode<Boolean> RELOAD = node("admin.reload",   defaultTo(OP_LEVEL));
    public static final PermissionNode<Boolean> SELLWAND = node("admin.sellwand", defaultTo(OP_LEVEL));

    @SubscribeEvent
    public static void onGatherPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BAL, PAY, SHOP, SELL, RELOAD, SELLWAND, AH);
    }

    /**
     * Predicate for Brigadier's {@code .requires(...)}. Resolves the node through
     * {@link PermissionAPI} for players, so a permission mod's grant is honoured;
     * for non-player sources (console, command blocks, functions) it defers to the vanilla permission-level check.
     */
    public static boolean check(CommandSourceStack src, PermissionNode<Boolean> node, int opLevel) {
        ServerPlayer player = src.getPlayer();
        if (player != null) {
            return PermissionAPI.getPermission(player, node);
        }
        // Console / command block / function: no player to resolve a node against.
        return src.hasPermission(opLevel);
    }

    // Helpers

    private static PermissionNode<Boolean> node(String path, PermissionNode.PermissionResolver<Boolean> resolver) {
        return new PermissionNode<>(
                ResourceLocation.fromNamespaceAndPath(MMOEcon.MOD_ID, path),
                PermissionTypes.BOOLEAN,
                resolver);
    }

    /**
     * Default resolver used when no permission mod is installed:
     * grant the node to anyone at or above {@code opLevel}.
     */
    private static PermissionNode.PermissionResolver<Boolean> defaultTo(int opLevel) {
        return (player, uuid, context) -> {
            if (opLevel <= 0) return true;
            return player != null && player.hasPermissions(opLevel);
        };
    }

    private Permissions() {}
}
