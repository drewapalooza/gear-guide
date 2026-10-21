package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import org.junit.Test;

public class GearRequirementsTest {
    @Test
    public void acceptsOrnamentedVoidWithBankedMeleeSwitch() {
        Snapshot s = GuideStoreTest.snapshot();
        int[] ids = {26475, 26469, 26471, 26467, 26477};
        int[] slots = {0, 4, 7, 9, 0};
        for (int i = 0; i < ids.length; i++)
            s.bank.add(GuideStoreTest.item(ids[i], "Ornamented Void", 1, slots[i], false));
        Setup build = GuideStoreTest.setup(s);
        build.equipment.put("HEAD", new Pick(26475, 1));
        build.equipment.put("BODY", new Pick(26469, 1));
        build.equipment.put("LEGS", new Pick(26471, 1));
        build.equipment.put("GLOVES", new Pick(26467, 1));
        GuideStoreTest.addInventory(build, 0, 26477, 1);
        GuideStore.validate(build, s);
        GearRequirements.enrich(s);
        assertTrue(s.bank.get(s.bank.size() - 1).levelRequirementsKnown);
    }

    @Test
    public void ornamentedVoidStillRejectsMissingRequiredLevels() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.put("PRAYER", 21);
        s.bank.add(GuideStoreTest.item(26477, "Void melee helm (or)", 1, 0, false));
        Setup build = GuideStoreTest.setup(s);
        GuideStoreTest.addInventory(build, 0, 26477, 1);
        GuideStoreTest.invalid(build, s, "PRAYER 22");
    }

    @Test
    public void rejectsOwnedWeaponAboveAttackLevel() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.put("ATTACK", 69);
        try {
            GuideStore.validate(GuideStoreTest.setup(s), s);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("ATTACK 70"));
        }
    }

    @Test
    public void checksAllScytheRequirements() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.put("ATTACK", 79);
        s.levels.put("STRENGTH", 89);
        String reason = GearRequirements.blocked(22325, s);
        assertTrue(reason.contains("ATTACK 80"));
        assertTrue(reason.contains("STRENGTH 90"));
    }

    @Test
    public void requirementsUpdateWhenPlayerLevelsUp() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.put("ATTACK", 76);
        assertFalse(GearRequirements.blocked(29589, s).isEmpty());
        s.levels.put("ATTACK", 77);
        assertEquals("", GearRequirements.blocked(29589, s));
    }

    @Test
    public void unknownEquipmentIsNotAssumedUsable() {
        assertTrue(
                GearRequirements.blocked(999999, GuideStoreTest.snapshot())
                        .contains("not verified"));
    }

    @Test
    public void currentlyEquippedItemProvesUnknownRequirementEligibility() {
        Snapshot s = GuideStoreTest.snapshot();
        s.equipment.add(GuideStoreTest.item(999999, "Unknown worn gear", 1, 3, false));
        assertEquals("", GearRequirements.blocked(999999, s));
    }

    @Test
    public void missingSkillIsNotAssumed99() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.remove("ATTACK");
        assertTrue(GearRequirements.blocked(29589, s).contains("unknown"));
    }

    @Test
    public void inventorySwitchIsValidated() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.put("DEFENCE", 1);
        Setup build = GuideStoreTest.setup(s);
        GuideStoreTest.addInventory(build, 0, 11840, 1);
        try {
            GuideStore.validate(build, s);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("DEFENCE 60"));
        }
    }

    @Test
    public void enrichedSnapshotIncludesBlockReason() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.put("ATTACK", 1);
        GearRequirements.enrich(s);
        assertTrue(s.bank.get(0).levelRequirementsKnown);
        assertTrue(s.bank.get(0).levelBlock.contains("ATTACK"));
    }

    @Test
    public void dukeUpgradesAreTaskSpecific() {
        Setup s = new Setup();
        s.enemyType = "duke-sucellus";
        assertEquals(2, GearUpgrades.choices(s, "WEAPON", 19675).size());
        s.enemyType = "bloodvelds";
        assertTrue(GearUpgrades.choices(s, "WEAPON", 19675).isEmpty());
    }

    @Test
    public void upgradeShowsBothOwnershipAndLevelBlocks() {
        Snapshot s = GuideStoreTest.snapshot();
        String text = GearUpgrades.status(new Upgrade(22325, "Scythe", "Best", ""), s, 19675);
        assertTrue(text.contains("Not owned"));
        assertTrue(text.contains("ATTACK 80"));
        s.bank.add(GuideStoreTest.item(22325, "Scythe", 1, 3, false));
        assertTrue(
                GearUpgrades.status(new Upgrade(22325, "Scythe", "Best", ""), s, 19675)
                        .contains("Owned, not selected"));
    }

    @Test
    public void knownUsableOwnedUpgradeIsVisible() {
        Snapshot s = GuideStoreTest.snapshot();
        s.levels.put("ATTACK", 80);
        s.bank.add(GuideStoreTest.item(29589, "Emberlight", 1, 3, false));
        String t = GearUpgrades.status(new Upgrade(29589, "Emberlight", "Better", ""), s, 19675);
        assertTrue(t.contains("Owned, not selected"));
        assertTrue(t.contains("Levels met"));
    }

    @Test
    public void tooltipEscapesProviderText() {
        Setup s = new Setup();
        s.enemyType = "duke";
        assertFalse(
                GearUpgrades.tooltip(s, GuideStoreTest.snapshot(), 19675, "WEAPON", "<img src=x>")
                        .contains("<img"));
    }
}
