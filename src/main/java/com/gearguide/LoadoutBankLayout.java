package com.gearguide;

import static com.gearguide.Models.*;

import net.runelite.client.plugins.banktags.tabs.Layout;

import java.util.Map;

/** Equipment and inventory retain their positions; rune supplies get their own rows below. */
final class LoadoutBankLayout {
    private static final Map<String, Integer> POSITIONS =
            Map.ofEntries(
                    Map.entry("HEAD", 1),
                    Map.entry("CAPE", 8),
                    Map.entry("AMULET", 9),
                    Map.entry("AMMO", 11),
                    Map.entry("WEAPON", 16),
                    Map.entry("BODY", 17),
                    Map.entry("SHIELD", 18),
                    Map.entry("LEGS", 25),
                    Map.entry("GLOVES", 32),
                    Map.entry("BOOTS", 33),
                    Map.entry("RING", 34));

    static Layout build(String tag, Setup setup) {
        Layout layout = new Layout(tag);
        setup.equipment.forEach(
                (slot, pick) -> layout.setItemAtPos(pick.itemId, POSITIONS.get(slot)));
        for (InventoryPick p : setup.inventory)
            layout.setItemAtPos(p.item.itemId, (p.slot / 4) * 8 + 4 + p.slot % 4);
        for (int i = 0; i < setup.runes.size(); i++)
            layout.setItemAtPos(setup.runes.get(i).itemId, 64 + i);
        return layout;
    }
}
