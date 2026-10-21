package com.gearguide;

import java.util.Locale;

final class RuneSupplies {
    static final int MAX_TYPES = 32;

    static boolean isRune(Models.OwnedItem item) {
        return item != null
                && !item.noted
                && item.stackable
                && item.name != null
                && item.name.toLowerCase(Locale.ROOT).endsWith(" rune");
    }
}
