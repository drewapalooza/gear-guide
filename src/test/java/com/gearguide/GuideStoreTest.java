package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.Gson;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.nio.file.*;

public class GuideStoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    static Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.accountId = "test-account";
        s.bankKnown = true;
        s.levels.put("ATTACK", 75);
        for (String skill :
                new String[] {
                    "DEFENCE",
                    "STRENGTH",
                    "RANGED",
                    "MAGIC",
                    "PRAYER",
                    "HITPOINTS",
                    "AGILITY",
                    "SLAYER"
                }) s.levels.put(skill, 99);
        s.bank.add(item(4151, "Abyssal whip", 1, 3, false));
        s.bank.add(item(11840, "Dragon boots", 1, 10, false));
        s.bank.add(item(385, "Shark", 4, null, false));
        s.bank.add(item(892, "Rune arrow", 100, 13, true));
        s.revision = GuideStore.fingerprint(s);
        return s;
    }

    static OwnedItem item(int id, String name, long qty, Integer slot, boolean stackable) {
        OwnedItem i = new OwnedItem();
        i.id = id;
        i.rawId = id;
        i.name = name;
        i.quantity = qty;
        i.equipmentSlot = slot;
        i.stackable = stackable;
        return i;
    }

    static Setup setup(Snapshot s) {
        Setup t = new Setup();
        t.id = "bloodvelds";
        t.name = "Bloodvelds";
        t.accountId = s.accountId;
        t.basedOnRevision = s.revision;
        t.equipment.put("WEAPON", new Pick(4151, 1));
        return t;
    }

    static void addInventory(Setup t, int slot, int id, long quantity) {
        InventoryPick i = new InventoryPick();
        i.slot = slot;
        i.item = new Pick(id, quantity);
        t.inventory.add(i);
    }

    static void invalid(Setup t, Snapshot s, String contains) {
        try {
            GuideStore.validate(t, s);
            fail("Accepted invalid setup");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(contains));
        }
    }

    @Test
    public void acceptsValidGearAndStackableInventory() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        addInventory(t, 0, 892, 100);
        addInventory(t, 1, 385, 1);
        GuideStore.validate(t, s);
    }

    @Test
    public void rejectsUnknownAndUnownedItems() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        t.equipment.get("WEAPON").itemId = 999;
        invalid(t, s, "not in");
    }

    @Test
    public void rejectsWrongAccountAndStaleRevision() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        t.accountId = "other";
        invalid(t, s, "accountId");
        t.accountId = s.accountId;
        t.basedOnRevision = "old";
        invalid(t, s, "changed");
    }

    @Test
    public void rejectsWrongEquipmentSlot() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        t.equipment.put("HEAD", new Pick(11840, 1));
        invalid(t, s, "cannot be worn");
    }

    @Test
    public void rejectsTwoHandedAndShield() {
        Snapshot s = snapshot();
        s.bank.get(0).twoHanded = true;
        s.bank.add(item(11283, "Shield", 1, 5, false));
        Setup t = setup(s);
        t.equipment.put("SHIELD", new Pick(11283, 1));
        invalid(t, s, "Two-handed");
    }

    @Test
    public void countsEquipmentAndInventoryTogether() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        addInventory(t, 0, 4151, 1);
        invalid(t, s, "Not enough");
    }

    @Test
    public void preventsDuplicateInventorySlots() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        addInventory(t, 0, 385, 1);
        addInventory(t, 0, 385, 1);
        invalid(t, s, "unique");
    }

    @Test
    public void preventsNonStackableOverfill() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        addInventory(t, 0, 385, 2);
        invalid(t, s, "separate");
    }

    @Test
    public void rejectsZeroQuantity() {
        Snapshot s = snapshot();
        Setup t = setup(s);
        t.equipment.get("WEAPON").quantity = 0;
        invalid(t, s, "quantity");
    }

    @Test
    public void movingItemsDoesNotInvalidateRevision() {
        Snapshot s = snapshot();
        String before = GuideStore.fingerprint(s);
        s.equipment.add(s.bank.remove(0));
        assertEquals(before, GuideStore.fingerprint(s));
        s.levels.put("ATTACK", 76);
        assertNotEquals(before, GuideStore.fingerprint(s));
    }

    @Test
    public void persistsAndSeparatesAccounts() throws Exception {
        Path root = temp.getRoot().toPath();
        GuideStore store = new GuideStore(root, new Gson());
        Snapshot s = snapshot();
        store.publish(s);
        store.put(setup(s));
        GuideStore restarted = new GuideStore(root, new Gson());
        restarted.publish(s);
        assertEquals("bloodvelds", restarted.active().id);
        Snapshot other = snapshot();
        other.accountId = "second-account";
        restarted.publish(other);
        assertNull(restarted.active());
        restarted.clear();
        assertNull(restarted.snapshot());
        restarted.publish(s);
        assertEquals("bloodvelds", restarted.active().id);
    }

    @Test
    public void failedValidationDoesNotOverwriteSavedSetup() throws Exception {
        GuideStore store = new GuideStore(temp.getRoot().toPath(), new Gson());
        Snapshot s = snapshot();
        store.publish(s);
        store.put(setup(s));
        Setup t = setup(s);
        t.name = "Bad overwrite";
        t.basedOnRevision = "old";
        try {
            store.put(t);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        assertEquals("Bloodvelds", store.active().name);
    }

    @Test
    public void deletePersistsAndClearsSelection() throws Exception {
        GuideStore store = new GuideStore(temp.getRoot().toPath(), new Gson());
        Snapshot s = snapshot();
        store.publish(s);
        store.put(setup(s));
        store.delete("bloodvelds");
        assertNull(store.active());
        assertTrue(store.saved().setups.isEmpty());
    }
}
