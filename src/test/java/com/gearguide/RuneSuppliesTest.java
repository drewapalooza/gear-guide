package com.gearguide;

import static com.gearguide.GuideStoreTest.*;
import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.Gson;

import net.runelite.client.plugins.banktags.tabs.Layout;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.*;

public class RuneSuppliesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Snapshot bank() {
        Snapshot s = snapshot();
        s.bank.add(item(565, "Blood rune", 1000, null, true));
        s.bank.add(item(560, "Death rune", 1000, null, true));
        s.bank.add(item(555, "Water rune", 4000, null, true));
        s.bank.add(item(563, "Law rune", 100, null, true));
        s.revision = GuideStore.fingerprint(s);
        return s;
    }

    @Test
    public void addsRuneRowsWithoutReplacingFullInventoryOrEquipment() {
        Snapshot s = bank();
        Setup t = setup(s);
        s.bank.get(2).quantity = 28;
        for (int slot = 0; slot < 28; slot++) addInventory(t, slot, 385, 1);
        t.runes.addAll(
                Arrays.asList(
                        new Pick(565, 1000),
                        new Pick(560, 1000),
                        new Pick(555, 4000),
                        new Pick(563, 100)));
        GuideStore.validate(t, s);
        Layout layout = LoadoutBankLayout.build("test", t);
        assertEquals(4151, layout.getItemAtPos(16));
        for (int slot = 0; slot < 28; slot++)
            assertEquals(385, layout.getItemAtPos((slot / 4) * 8 + 4 + slot % 4));
        for (int i = 0; i < 4; i++)
            assertEquals(t.runes.get(i).itemId, layout.getItemAtPos(64 + i));
        assertEquals(33, t.picks().size());
    }

    @Test
    public void countsLooseInventoryRunesAndExtraSuppliesTogether() {
        Snapshot s = bank();
        Setup t = setup(s);
        addInventory(t, 0, 565, 600);
        t.runes.add(new Pick(565, 500));
        invalid(t, s, "short 100");
        t.runes.get(0).quantity = 400;
        GuideStore.validate(t, s);
        Layout layout = LoadoutBankLayout.build("test", t);
        assertEquals(565, layout.getItemAtPos(4));
        assertEquals(565, layout.getItemAtPos(64));
    }

    @Test
    public void rejectsDuplicateRuneTypes() {
        Snapshot s = bank();
        Setup t = setup(s);
        t.runes.add(new Pick(565, 100));
        t.runes.add(new Pick(565, 100));
        invalid(t, s, "unique rune");
    }

    @Test
    public void rejectsNonRunesAndNotedRunes() {
        Snapshot s = bank();
        Setup t = setup(s);
        t.runes.add(new Pick(892, 1));
        invalid(t, s, "unnoted runes");
        t.runes.set(0, new Pick(565, 1));
        s.bank.get(4).noted = true;
        invalid(t, s, "unnoted runes");
    }

    @Test
    public void rejectsMalformedAndUnownedSupplies() {
        Snapshot s = bank();
        Setup t = setup(s);
        t.runes.add(null);
        invalid(t, s, "Invalid item");
        t.runes.set(0, new Pick(565, 0));
        invalid(t, s, "quantity");
        t.runes.set(0, new Pick(556, 1));
        invalid(t, s, "not in");
        t.runes = null;
        invalid(t, s, "Invalid rune supplies");
        t.runes = new ArrayList<>(Collections.nCopies(33, new Pick(565, 1)));
        invalid(t, s, "maximum 32");
    }

    @Test
    public void olderJsonDefaultsToNoExtraSuppliesWithoutParsingNotes() {
        Setup t = new Gson().fromJson("{\"notes\":\"Bring 1000 blood runes\"}", Setup.class);
        assertTrue(t.runes.isEmpty());
        assertTrue(t.picks().isEmpty());
        assertEquals(0, LoadoutBankLayout.build("test", t).getLayout().length);
    }

    @Test
    public void persistsRuneRequirementsAcrossRestart() throws Exception {
        Snapshot s = bank();
        Path root = temp.getRoot().toPath();
        GuideStore store = new GuideStore(root, new Gson());
        store.publish(s);
        Setup t = setup(s);
        t.runes.add(new Pick(565, 1000));
        t.runes.get(0).note = "Pouch refill";
        store.put(t);
        GuideStore restarted = new GuideStore(root, new Gson());
        restarted.publish(s);
        Pick rune = restarted.active().runes.get(0);
        assertEquals(565, rune.itemId);
        assertEquals(1000, rune.quantity);
        assertEquals("Pouch refill", rune.note);
        assertEquals(565, LoadoutBankLayout.build("test", restarted.active()).getItemAtPos(64));
    }
}
