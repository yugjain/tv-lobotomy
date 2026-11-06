package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;

/** Plain JVM checks for the gold formatting and date parsing (no Android classes involved). */
public class GoldConfigTest {

    @Test
    public void priceUsesIndianLakhGrouping() {
        assertEquals("₹1,37,524", GoldConfig.formatPrice(137524));
        assertEquals("₹1,00,000", GoldConfig.formatPrice(100000));
        assertEquals("₹12,34,567", GoldConfig.formatPrice(1234567));
        assertEquals("₹999", GoldConfig.formatPrice(999));
    }

    @Test
    public void purityBecomesKaratOrTheNumber() {
        assertEquals("24K", GoldConfig.karatLabel(999));
        assertEquals("22K", GoldConfig.karatLabel(916));
        assertEquals("995", GoldConfig.karatLabel(995));
        assertEquals("750", GoldConfig.karatLabel(750));
        assertEquals("585", GoldConfig.karatLabel(585));
    }

    @Test
    public void knownDateFormsParse() {
        assertEquals(LocalDate.of(2026, 11, 5), GoldConfig.parseDate("2026-11-05"));
        assertEquals(LocalDate.of(2026, 11, 5), GoldConfig.parseDate("05/11/2026"));
        assertEquals(LocalDate.of(2026, 11, 5), GoldConfig.parseDate("5 Nov 2026"));
        assertNull(GoldConfig.parseDate("not a date"));
    }
}
