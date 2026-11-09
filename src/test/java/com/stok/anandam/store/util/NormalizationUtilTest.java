package com.stok.anandam.store.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Paritas dengan fungsi Postgres movva_normalize_item():
 * uppercase, buang ™/®, ganti non A-Z0-9 dengan spasi, collapse spasi, trim.
 */
class NormalizationUtilTest {

    @Test
    void normalizeItemName_matchesExpectedPostgresSemantics() {
        assertEquals("", NormalizationUtil.normalizeItemName(null));
        assertEquals("", NormalizationUtil.normalizeItemName("   "));
        assertEquals("A B C", NormalizationUtil.normalizeItemName("  a/b  c  "));
        assertEquals("LOGITECH M90", NormalizationUtil.normalizeItemName(" logitech™ m90 "));
        assertEquals("LOGITECHM90", NormalizationUtil.normalizeItemName("LOGITECH™M90"));
        assertEquals("EPSON L3210", NormalizationUtil.normalizeItemName("Epson® L3210"));
        assertEquals("PC HITAM KACA", NormalizationUtil.normalizeItemName("PC HITAM (KACA)"));
        assertEquals("RAM HYNIX DDR4 8GB 2666", NormalizationUtil.normalizeItemName("RAM HYNIX DDR4 8GB/2666"));
        assertEquals("MB VURRION H61M D3H 5699", NormalizationUtil.normalizeItemName("MB VURRION H61M-D3H(5699)"));
        assertEquals("HD INT 3 5 WDC 1TB", NormalizationUtil.normalizeItemName("HD INT 3.5 WDC 1TB"));
    }
}
