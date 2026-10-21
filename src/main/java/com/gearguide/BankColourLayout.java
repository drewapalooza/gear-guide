package com.gearguide;

import static com.gearguide.Models.*;

import net.runelite.client.plugins.banktags.tabs.Layout;

import java.util.*;

/** A virtual layout; the input bank slots are never mutated. */
final class BankColourLayout {
    static List<OwnedItem> sorted(Collection<OwnedItem> items, Map<Integer, Integer> overrides) {
        List<OwnedItem> result = new ArrayList<>(items);
        result.sort(
                Comparator.comparingInt((OwnedItem i) -> BankOrganizer.choose(i, overrides).tab)
                        .thenComparing(
                                i -> i.name == null ? "" : i.name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(i -> i.id));
        return result;
    }

    static Layout build(String tag, Collection<OwnedItem> items, Map<Integer, Integer> overrides) {
        Layout layout = new Layout(tag);
        int position = 0, previous = 0;
        Set<Integer> seen = new HashSet<>();
        for (OwnedItem item : sorted(items, overrides)) {
            if (!seen.add(item.id)) continue;
            int group = BankOrganizer.choose(item, overrides).tab;
            if (previous != 0 && group != previous) position = ((position + 7) / 8) * 8 + 8;
            layout.setItemAtPos(item.id, position++);
            previous = group;
        }
        return layout;
    }
}
