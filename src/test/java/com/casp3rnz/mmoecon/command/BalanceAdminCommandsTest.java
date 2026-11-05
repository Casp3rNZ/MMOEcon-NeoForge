package com.casp3rnz.mmoecon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the real Brigadier tree and non-player permission-level predicates. */
class BalanceAdminCommandsTest {

    @Test
    void allAdminBranchesRequireLevelTwo() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        BalanceAdminCommands.register(dispatcher);
        var root = dispatcher.getRoot().getChild("bal");
        assertEquals(Set.of("get", "set", "add", "subtract", "admin"),
                root.getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet()));
        assertTrue(root.canUse(source(0)));
        for (var child : root.getChildren()) {
            assertFalse(child.canUse(source(0)), child.getName());
            assertFalse(child.canUse(source(1)), child.getName());
            assertTrue(child.canUse(source(2)), child.getName());
            assertTrue(child.canUse(source(4)), child.getName());
        }
    }

    @Test
    void ordinarySourcesCannotParseAdminCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        BalanceAdminCommands.register(dispatcher);
        for (String command : new String[] { "bal get someone", "bal set someone 12.34",
                "bal add someone 1", "bal subtract someone 1", "bal admin" }) {
            var parsed = dispatcher.parse(command, source(0));
            assertTrue(parsed.getReader().canRead(), command);
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(parsed), command);
        }
    }

    @Test
    void privilegedSourcesParseDecimalTextWithoutDoubleConversion() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        BalanceAdminCommands.register(dispatcher);
        for (String command : new String[] { "bal get someone", "bal set someone 92233720368547758.07",
                "bal add someone 0.01", "bal subtract someone 1", "bal admin" }) {
            var parsed = dispatcher.parse(command, source(2));
            assertFalse(parsed.getReader().canRead(), command);
            assertTrue(parsed.getExceptions().isEmpty(), command);
        }
    }

    @Test
    void adminRegistrationPreservesExistingCommandsAndAliases() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        EconomyCommands.onRegisterCommands(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, null));
        assertNotNull(dispatcher.getRoot().getChild("bal").getCommand());
        assertNotNull(dispatcher.getRoot().getChild("money").getCommand());
        assertNotNull(dispatcher.getRoot().getChild("bal").getChild("top").getCommand());
        for (String command : new String[] { "bal", "money", "bal top", "pay someone 0.01", "shop",
                "sell hand", "sell inv", "sell receipt", "ah", "ah sell 12.34", "auction", "auctionhouse" }) {
            var parsed = dispatcher.parse(command, source(0));
            assertFalse(parsed.getReader().canRead(), command);
            assertTrue(parsed.getExceptions().isEmpty(), command);
        }
        assertNull(dispatcher.getRoot().getChild("money").getChild("set"));
        for (String command : new String[] { "bal get someone", "bal set someone 12.34", "bal admin" }) {
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source(0)), command);
            assertFalse(dispatcher.parse(command, source(2)).getReader().canRead(), command);
        }
    }

    private static CommandSourceStack source(int permissionLevel) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null,
                permissionLevel, "Test console", Component.literal("Test console"), null, null);
    }
}
