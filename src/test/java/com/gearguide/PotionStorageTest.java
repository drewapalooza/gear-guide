package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class PotionStorageTest {
    private Snapshot bank(long doses) {
        Snapshot s = GuideStoreTest.snapshot();
        s.potionStorageKnown = true;
        PotionStock p = new PotionStock();
        p.enumId = 100;
        p.totalDoses = doses;
        p.withdrawDoses = 4;
        p.variants.put(4, GuideStoreTest.item(2434, "Prayer potion(4)", doses / 4, null, false));
        p.variants.put(3, GuideStoreTest.item(139, "Prayer potion(3)", doses / 3, null, false));
        p.variants.put(1, GuideStoreTest.item(143, "Prayer potion(1)", doses, null, false));
        s.potionStorage.add(p);
        s.revision = GuideStore.fingerprint(s);
        return s;
    }

    @Test
    public void storageOnlyPotionsAreAccepted() {
        Snapshot s = bank(8);
        Setup t = GuideStoreTest.setup(s);
        GuideStoreTest.addInventory(t, 0, 2434, 1);
        GuideStoreTest.addInventory(t, 1, 2434, 1);
        GuideStore.validate(t, s);
    }

    @Test
    public void cannotDoubleCountDifferentDoseVariants() {
        Snapshot s = bank(6);
        Setup t = GuideStoreTest.setup(s);
        GuideStoreTest.addInventory(t, 0, 2434, 1);
        GuideStoreTest.addInventory(t, 1, 139, 1);
        GuideStoreTest.invalid(t, s, "Not enough");
    }

    @Test
    public void combinesExactBankBottlesWithSharedPool() {
        Snapshot s = bank(3);
        s.bank.add(GuideStoreTest.item(2434, "Prayer potion(4)", 1, null, false));
        s.revision = GuideStore.fingerprint(s);
        Setup t = GuideStoreTest.setup(s);
        GuideStoreTest.addInventory(t, 0, 2434, 1);
        GuideStoreTest.addInventory(t, 1, 139, 1);
        GuideStore.validate(t, s);
    }

    @Test
    public void remainderCannotBecomeFullBottle() {
        Snapshot s = bank(3);
        assertEquals(Long.valueOf(0), s.counts().get(2434));
        Setup t = GuideStoreTest.setup(s);
        GuideStoreTest.addInventory(t, 0, 2434, 1);
        GuideStoreTest.invalid(t, s, "Not enough");
    }

    @Test
    public void fullAndPartialBottleUseSamePoolCorrectly() {
        Snapshot s = bank(5);
        Setup t = GuideStoreTest.setup(s);
        GuideStoreTest.addInventory(t, 0, 2434, 1);
        GuideStoreTest.addInventory(t, 1, 143, 1);
        GuideStore.validate(t, s);
    }

    @Test
    public void unknownStorageIsNotCounted() {
        Snapshot s = bank(100);
        s.potionStorageKnown = false;
        assertFalse(s.counts().containsKey(2434));
        assertEquals(Long.valueOf(1), s.shortages(Map.of(2434, 1L)).get(2434));
    }

    @Test
    public void withdrawalPreservesRevision() {
        Snapshot s = bank(8);
        String revision = GuideStore.fingerprint(s);
        s.potionStorage.get(0).totalDoses = 4;
        s.inventory.add(GuideStoreTest.item(2434, "Prayer potion(4)", 1, null, false));
        assertEquals(revision, GuideStore.fingerprint(s));
        s.potionStorage.get(0).totalDoses = 3;
        assertNotEquals(revision, GuideStore.fingerprint(s));
    }

    @Test
    public void doseSelectorDoesNotChangeOwnership() {
        Snapshot s = bank(8);
        String revision = GuideStore.fingerprint(s);
        s.potionStorage.get(0).withdrawDoses = 1;
        assertEquals(revision, GuideStore.fingerprint(s));
    }

    @Test
    public void poolsDoNotLeakAcrossPotionFamilies() {
        Snapshot s = bank(3);
        PotionStock other = new PotionStock();
        other.enumId = 200;
        other.totalDoses = 100;
        other.variants.put(4, GuideStoreTest.item(2440, "Super strength(4)", 25, null, false));
        s.potionStorage.add(other);
        assertEquals(Long.valueOf(1), s.shortages(Map.of(2434, 1L)).get(2434));
    }
}
