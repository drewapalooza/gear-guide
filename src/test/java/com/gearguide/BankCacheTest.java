package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import com.google.gson.Gson;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.nio.file.*;

public class BankCacheTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private GuideStore store() {
        return new GuideStore(TestFiles.root(temp.getRoot().toPath()), new Gson());
    }

    private Snapshot live() {
        Snapshot s = GuideStoreTest.snapshot();
        s.bankOpen = true;
        s.bankCapturedAt = "2026-09-29T18:00:00Z";
        return s;
    }

    private Snapshot login(String account) {
        Snapshot s = new Snapshot();
        s.accountId = account;
        return s;
    }

    @Test
    public void bankRestoresAfterRestartWithoutOpeningIt() throws Exception {
        GuideStore first = store();
        first.publish(live());
        GuideStore second = store();
        Snapshot next = login("test-account");
        second.publish(next);
        assertTrue(next.bankKnown);
        assertTrue(next.bankFromDisk);
        assertFalse(next.bankOpen);
        assertEquals(4, next.bank.size());
        assertEquals("2026-09-29T18:00:00Z", next.bankCapturedAt);
        assertTrue(next.inventory.isEmpty());
        assertFalse(next.potionStorageKnown);
    }

    @Test
    public void doesNotRestoreOtherAccountsBank() throws Exception {
        GuideStore first = store();
        first.publish(live());
        Snapshot other = login("other-account");
        first.publish(other);
        assertFalse(other.bankKnown);
        assertTrue(other.bank.isEmpty());
    }

    @Test
    public void freshBankReplacesCachedQuantities() throws Exception {
        GuideStore first = store();
        first.publish(live());
        Snapshot update = live();
        update.bank.clear();
        first.publish(update);
        Snapshot next = login("test-account");
        store().publish(next);
        assertTrue(next.bankKnown);
        assertTrue(next.bank.isEmpty());
    }

    @Test
    public void currentEquipmentAndTaskArePreserved() throws Exception {
        store().publish(live());
        Snapshot next = login("test-account");
        next.task.name = "Wyrms";
        next.equipment.add(GuideStoreTest.item(11840, "Dragon boots", 1, 10, false));
        store().publish(next);
        assertEquals("Wyrms", next.task.name);
        assertEquals(1, next.equipment.size());
    }

    @Test
    public void corruptCacheFallsBackToUnknownAndRecovers() throws Exception {
        Files.writeString(temp.getRoot().toPath().resolve("test-account-bank.json"), "broken");
        GuideStore s = store();
        Snapshot next = login("test-account");
        s.publish(next);
        assertFalse(next.bankKnown);
        s.publish(live());
        Snapshot restored = login("test-account");
        store().publish(restored);
        assertTrue(restored.bankKnown);
    }

    @Test
    public void unknownBankNeverOverwritesSavedCache() throws Exception {
        GuideStore s = store();
        s.publish(live());
        s.clear();
        s.publish(login("test-account"));
        Snapshot next = login("test-account");
        store().publish(next);
        assertEquals(4, next.bank.size());
    }
}
