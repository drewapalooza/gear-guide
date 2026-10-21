package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import net.runelite.client.plugins.banktags.tabs.Layout;

import org.junit.Test;

import java.util.*;

public class BankColourLayoutTest {
    private OwnedItem item(int id, String name) {
        OwnedItem i = new OwnedItem();
        i.id = id;
        i.name = name;
        return i;
    }

    @Test
    public void groupsOnlySuppliedTabItemsWithoutMutatingSource() {
        List<OwnedItem> source =
                new ArrayList<>(
                        Arrays.asList(
                                item(385, "Shark"),
                                item(560, "Death rune"),
                                item(556, "Air rune")));
        Layout l = BankColourLayout.build("test", source, Collections.emptyMap());
        assertEquals(385, source.get(0).id);
        assertEquals(556, l.getItemAtPos(0));
        assertEquals(560, l.getItemAtPos(1));
        Set<Integer> found = new HashSet<>();
        for (int id : l.getLayout()) if (id != -1) found.add(id);
        assertEquals(new HashSet<>(Arrays.asList(385, 560, 556)), found);
        int shark = -1;
        for (int i = 0; i < l.getLayout().length; i++) if (l.getItemAtPos(i) == 385) shark = i;
        assertTrue(shark >= 16);
        assertEquals(0, shark % 8);
    }

    @Test
    public void usesOverridesAndDeduplicatesVariants() {
        List<OwnedItem> source =
                Arrays.asList(item(385, "Shark"), item(385, "Shark"), item(556, "Air rune"));
        Layout l = BankColourLayout.build("test", source, Collections.singletonMap(385, 1));
        assertEquals(385, l.getItemAtPos(0));
        int count = 0;
        for (int id : l.getLayout()) if (id == 385) count++;
        assertEquals(1, count);
    }

    @Test
    public void tabNumbersHandleMarkupAndAvoidUnrelatedButtons() {
        assertEquals(3, BankTabOverlay.explicitTab("View tab <col=ff9040>3</col>"));
        assertTrue(BankTabOverlay.tabAction(new String[] {"View tab", "Collapse tab"}));
        assertFalse(BankTabOverlay.tabAction(new String[] {"New tab", "View all items"}));
        assertFalse(BankTabOverlay.tabAction(null));
    }
}
