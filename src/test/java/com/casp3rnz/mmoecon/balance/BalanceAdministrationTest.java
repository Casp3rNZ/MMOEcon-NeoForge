package com.casp3rnz.mmoecon.balance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.casp3rnz.mmoecon.balance.BalanceAdministration.Operation.*;
import static com.casp3rnz.mmoecon.balance.BalanceAdministration.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class BalanceAdministrationTest {

    @Test
    void setsExactBalanceIncludingZeroAndMaximum() {
        assertChange(1234L, SET, 0L, 0L);
        assertChange(1234L, SET, 1L, 1L);
        assertChange(1234L, SET, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    @Test
    void adjustsCentsAndAllowsSubtractingTheWholeBalance() {
        assertChange(1234L, ADD, 1L, 1235L);
        assertChange(1234L, SUBTRACT, 1L, 1233L);
        assertChange(1234L, SUBTRACT, 1234L, 0L);
        assertChange(0L, ADD, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    @ParameterizedTest
    @EnumSource(BalanceAdministration.Operation.class)
    void rejectsNegativeAmountsWithoutChangingBalance(BalanceAdministration.Operation operation) {
        assertFailure(1234L, operation, -1L, INVALID_AMOUNT);
        assertFailure(1234L, operation, Long.MIN_VALUE, INVALID_AMOUNT);
    }

    @Test
    void rejectsZeroAdjustments() {
        assertFailure(1234L, ADD, 0L, INVALID_AMOUNT);
        assertFailure(1234L, SUBTRACT, 0L, INVALID_AMOUNT);
    }

    @Test
    void rejectsInsufficientFundsWithoutClampingOrPartialDeduction() {
        assertFailure(1234L, SUBTRACT, 1235L, INSUFFICIENT_FUNDS);
        assertFailure(0L, SUBTRACT, 1L, INSUFFICIENT_FUNDS);
    }

    @Test
    void rejectsOverflowAtBothBoundaries() {
        assertChange(Long.MAX_VALUE - 1L, ADD, 1L, Long.MAX_VALUE);
        assertFailure(Long.MAX_VALUE, ADD, 1L, OVERFLOW);
        assertFailure(1L, ADD, Long.MAX_VALUE, OVERFLOW);
    }

    @Test
    void onlySetCanRepairCorruptNegativeBalances() {
        assertFailure(-1L, ADD, 1L, INVALID_BALANCE);
        assertFailure(Long.MIN_VALUE, SUBTRACT, 1L, INVALID_BALANCE);
        assertChange(Long.MIN_VALUE, SET, 0L, 0L);
    }

    private static void assertChange(long before, BalanceAdministration.Operation operation,
                                     long amount, long after) {
        var result = BalanceAdministration.calculate(before, operation, amount);
        assertTrue(result.success());
        assertEquals(before, result.previousBalance());
        assertEquals(after, result.balance());
    }

    private static void assertFailure(long before, BalanceAdministration.Operation operation,
                                      long amount, BalanceAdministration.Status status) {
        var result = BalanceAdministration.calculate(before, operation, amount);
        assertFalse(result.success());
        assertEquals(status, result.status());
        assertEquals(before, result.previousBalance());
        assertEquals(before, result.balance());
    }
}
