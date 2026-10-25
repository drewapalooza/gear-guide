package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.file.*;
import java.util.*;

public class BankOrganizerTest {
    private static OwnedItem item(int id, String name) {
        OwnedItem i = new OwnedItem();
        i.id = id;
        i.name = name;
        return i;
    }

    @Test
    public void fullCatalogueIsBundled() {
        assertTrue(BankOrganizer.catalogueSize() > 30000);
    }

    private static void verified(int id, int tab) {
        BankOrganizer.Choice c =
                BankOrganizer.choose(
                        item(id, "misleading hammer bolt staff name"), Collections.emptyMap());
        assertEquals(tab, c.tab);
        assertFalse(c.reason, c.review);
    }

    @Test
    public void wikiItemIdentityWinsOverMisleadingNames() {
        verified(30634, 4);
        verified(21028, 5);
        verified(21742, 2);
        verified(2347, 8);
        verified(31398, 9);
        verified(28357, 9);
        verified(7462, 2);
        verified(31097, 2);
    }

    @Test
    public void variantsAndIngredientsUseTheirActualFunction() {
        verified(3024, 6);
        verified(3028, 6);
        verified(11194, 1);
        verified(385, 5);
        verified(386, 5);
        verified(43, 8);
        verified(19580, 8);
        verified(31475, 8);
        verified(1753, 8);
    }

    @Test
    public void sourceIsVisibleAndUnknownNameGuessesNeedReview() {
        assertTrue(
                BankOrganizer.choose(item(30634, "Twinflame staff"), Collections.emptyMap())
                        .reason
                        .contains("OSRS Wiki"));
        BankOrganizer.Choice c =
                BankOrganizer.choose(item(999999, "Granite hammer"), Collections.emptyMap());
        assertEquals(2, c.tab);
        assertTrue(c.review);
        assertTrue(c.reason.contains("Unverified"));
    }

    @Test
    public void futureItemsStillGetAHighlight() {
        BankOrganizer.Choice c =
                BankOrganizer.choose(
                        item(999999, "Unfamiliar future item"), Collections.emptyMap());
        assertTrue(c.tab >= 1 && c.tab <= 9);
        assertTrue(c.review);
    }

    @Test
    public void playerOverrideWinsOverCatalogue() {
        assertEquals(
                7, BankOrganizer.choose(item(385, "Shark"), Collections.singletonMap(385, 7)).tab);
    }

    @Test
    public void distinguishesCombatHammerFromTool() {
        assertEquals(2, BankOrganizer.classify(item(21742, "Granite hammer")).tab);
        assertEquals(8, BankOrganizer.classify(item(2347, "Hammer")).tab);
    }

    @Test
    public void ingredientsTakePriorityOverFood() {
        assertEquals(6, BankOrganizer.classify(item(1, "Potato cactus")).tab);
        assertEquals(5, BankOrganizer.classify(item(2, "Tuna potato")).tab);
    }

    @Test
    public void foodAndRunesAndTravel() {
        assertEquals(5, BankOrganizer.classify(item(385, "Shark")).tab);
        assertEquals(4, BankOrganizer.classify(item(560, "Death rune")).tab);
        assertEquals(1, BankOrganizer.classify(item(1, "Teleport to house")).tab);
    }

    @Test
    public void unknownItemsAreNotAssertedToBeQuestItems() {
        BankOrganizer.Choice c = BankOrganizer.classify(item(1, "Unfamiliar object"));
        assertEquals(9, c.tab);
        assertTrue(c.review);
    }

    @Test
    public void overridePersistsAndResets() throws Exception {
        Path root = Files.createTempDirectory("bank-guide-test");
        String a = "aaaaaaaaaaaaaaaaaaaaaaaa";
        BankOrganizer o = new BankOrganizer(TestFiles.root(root), new com.google.gson.Gson());
        o.account(a);
        o.assign(a, 385, 2);
        BankOrganizer reload = new BankOrganizer(TestFiles.root(root), new com.google.gson.Gson());
        reload.account(a);
        assertEquals(2, BankOrganizer.choose(item(385, "Shark"), reload.overrides()).tab);
        reload.assign(a, 385, 0);
        assertEquals(5, BankOrganizer.choose(item(385, "Shark"), reload.overrides()).tab);
    }

    @Test
    public void separatesAccountsAndRejectsStaleEdits() throws Exception {
        BankOrganizer o =
                new BankOrganizer(
                        TestFiles.root(Files.createTempDirectory("bank-guide-test")),
                        new com.google.gson.Gson());
        String a = "aaaaaaaaaaaaaaaaaaaaaaaa", b = "bbbbbbbbbbbbbbbbbbbbbbbb";
        o.account(a);
        o.assign(a, 385, 2);
        o.account(b);
        assertTrue(o.overrides().isEmpty());
        try {
            o.assign(a, 385, 4);
            fail();
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void corruptAssignmentsArePreserved() throws Exception {
        Path root = Files.createTempDirectory("bank-guide-test");
        String a = "aaaaaaaaaaaaaaaaaaaaaaaa";
        Path file = root.resolve(a + "-bank-guide.json");
        Files.writeString(file, "not json");
        BankOrganizer o = new BankOrganizer(TestFiles.root(root), new com.google.gson.Gson());
        try {
            o.account(a);
            fail();
        } catch (java.io.IOException expected) {
        }
        assertEquals("not json", Files.readString(file));
        try {
            o.assign(a, 385, 2);
            fail();
        } catch (IllegalStateException expected) {
        }
    }
}
