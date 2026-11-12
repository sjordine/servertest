package com.servertest.products.common;

import java.math.BigDecimal;
import java.util.Locale;

/** Normalisation of business keys and display helpers. */
public final class Keys {

    /** Allowed characters for SKUs and package codes (surrounding spaces are trimmed). */
    public static final String CODE_PATTERN = "^\\s*[A-Za-z0-9][A-Za-z0-9._-]*\\s*$";

    private Keys() {}

    /** SKUs and package codes are trimmed and upper-cased, so "rice-1" and "RICE-1" are the same key. */
    public static String code(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    /** Unit codes are lower-case by convention (kg, ml, un). */
    public static String unitCode(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    /** 6.000 -> "6", 0.500 -> "0.5". */
    public static String quantity(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString();
    }

    /** "6 un", "0.5 l". */
    public static String label(BigDecimal quantity, String unitCode) {
        return quantity(quantity) + " " + unitCode;
    }
}
