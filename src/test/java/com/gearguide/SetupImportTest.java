package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.Gson;

import net.runelite.client.util.Filepath;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class SetupImportTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final Gson gson = new Gson();

    private Filepath file(String json) throws Exception {
        Filepath path = TestFiles.root(temp.getRoot().toPath()).joinSegment("selected.json");
        path.write(json);
        return path.rooted();
    }

    private void invalid(Setup setup, Snapshot snapshot, String expected) throws Exception {
        try {
            SetupImport.read(file(gson.toJson(setup)), gson, snapshot);
            fail("Accepted invalid setup");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains(expected));
        }
    }

    @Test
    public void validatesSelectedFileWithoutSavingOrRebindingMetadata() throws Exception {
        Snapshot snapshot = GuideStoreTest.snapshot();
        Setup original = GuideStoreTest.setup(snapshot);
        original.notes = "Préférer mêlée — manually reviewed";
        original.replaceExisting = true;
        GuideStore store = new GuideStore(TestFiles.root(temp.getRoot().toPath()), gson);
        store.publish(snapshot);
        Setup imported = SetupImport.read(file(gson.toJson(original)), gson, snapshot);
        assertEquals(original.notes, imported.notes);
        assertEquals(original.accountId, imported.accountId);
        assertEquals(original.basedOnRevision, imported.basedOnRevision);
        assertFalse(imported.replaceExisting);
        assertTrue(store.saved().setups.isEmpty());
        assertNull(store.active());
    }

    @Test
    public void rejectsWrongAccountAndStaleBank() throws Exception {
        Snapshot snapshot = GuideStoreTest.snapshot();
        Setup setup = GuideStoreTest.setup(snapshot);
        setup.accountId = "other-account";
        invalid(setup, snapshot, "accountId");
        setup.accountId = snapshot.accountId;
        setup.basedOnRevision = "stale";
        invalid(setup, snapshot, "changed");
    }

    @Test
    public void rejectsUnownedItemsAndUnknownCombatStyles() throws Exception {
        Snapshot snapshot = GuideStoreTest.snapshot();
        Setup setup = GuideStoreTest.setup(snapshot);
        setup.equipment.get("WEAPON").itemId = 999999;
        invalid(setup, snapshot, "not in");
        setup.equipment.get("WEAPON").itemId = 4151;
        setup.combatStyle = "arbitrary";
        invalid(setup, snapshot, "combatStyle");
    }

    @Test
    public void rejectsMalformedNullAndOversizedFiles() throws Exception {
        Snapshot snapshot = GuideStoreTest.snapshot();
        for (String json :
                new String[] {"not json", "null", " ".repeat(SetupImport.MAX_BYTES + 1)}) {
            try {
                SetupImport.read(file(json), gson, snapshot);
                fail("Accepted malformed or oversized setup");
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void rejectsUnknownBankAndPreservesRuneRequirements() throws Exception {
        Snapshot snapshot = GuideStoreTest.snapshot();
        snapshot.bank.add(GuideStoreTest.item(565, "Blood rune", 1000, null, true));
        Setup setup = GuideStoreTest.setup(snapshot);
        setup.runes.add(new Pick(565, 1000));
        Filepath file = file(gson.toJson(setup));
        assertEquals(1000, SetupImport.read(file, gson, snapshot).runes.get(0).quantity);
        snapshot.bankKnown = false;
        try {
            SetupImport.read(file, gson, snapshot);
            fail("Imported without a bank snapshot");
        } catch (IllegalStateException expected) {
        }
    }
}
