package com.casp3rnz.mmoecon.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({ "0, 0", "12, 1200", "12.3, 1230", "12.34, 1234", "0.01, 1",
            "00012.34, 1234", "92233720368547758.07, 9223372036854775807" })
    void parsesDollarTextExactly(String text, long cents) {
        assertEquals(cents, Money.parseExact(text));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "-1", "+1", "1.001", "1.000", "1e2", "NaN", "Infinity",
            "1,000", "$1", ".01", "1.", "1K", " 1", "1 " })
    void rejectsInvalidSyntaxAndPrecision(String text) {
        assertThrows(IllegalArgumentException.class, () -> Money.parseExact(text));
    }

    @Test
    void rejectsAmountsBeyondLongCents() {
        assertThrows(ArithmeticException.class, () -> Money.parseExact("92233720368547758.08"));
        assertThrows(ArithmeticException.class, () -> Money.parseExact("9999999999999999999999999"));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L, 100L, 1234L, 100000000L, Long.MAX_VALUE })
    void exactDisplayRoundTripsWithoutLosingCents(long cents) {
        assertEquals(cents, Money.parseExact(Money.formatExact(cents)));
    }

    @Test
    void formatsNegativeExtremesExactly() {
        assertEquals("-92233720368547758.08", Money.formatExact(Long.MIN_VALUE));
    }

    @Test
    void preservesExistingHalfUpBoundaryConversion() {
        assertEquals(825L, Money.fromDouble(8.245));
        assertEquals(123L, Money.fromDouble(1.234));
    }
}
