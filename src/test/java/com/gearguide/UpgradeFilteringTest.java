package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class UpgradeFilteringTest {
    private Setup melee() {
        Setup s = new Setup();
        s.combatStyle = "melee";
        return s;
    }

    @Test
    public void maxBootsHaveNoDowngrades() {
        assertTrue(GearUpgrades.choices(melee(), "BOOTS", 31097).isEmpty());
    }

    @Test
    public void tortureOnlyShowsRancour() {
        List<Upgrade> u = GearUpgrades.choices(melee(), "AMULET", 19553);
        assertEquals(1, u.size());
        assertEquals(29801, u.get(0).itemId);
    }

    @Test
    public void lowestBootsHaveTwoUpgrades() {
        assertEquals(2, GearUpgrades.choices(melee(), "BOOTS", 11840).size());
    }

    @Test
    public void topCosmeticVariantHasNoDowngrades() {
        assertTrue(GearUpgrades.choices(melee(), "AMULET", 29804).isEmpty());
    }

    @Test
    public void customLadderUsesRanksNotArrayOrder() {
        Setup s = melee();
        s.upgrades.put(
                "BOOTS",
                Arrays.asList(
                        new Upgrade(31097, "Max", "Best", ""),
                        new Upgrade(11840, "Dragon", "Good", ""),
                        new Upgrade(13239, "Primordial", "Better", "")));
        List<Upgrade> u = GearUpgrades.choices(s, "BOOTS", 13239);
        assertEquals(1, u.size());
        assertEquals(31097, u.get(0).itemId);
    }

    @Test
    public void unrankedSelectedGearDoesNotInventComparison() {
        Setup s = melee();
        s.upgrades.put("WEAPON", Arrays.asList(new Upgrade(4151, "Whip", "Best", "")));
        assertTrue(GearUpgrades.choices(s, "WEAPON", 29796).isEmpty());
    }

    @Test
    public void sameTierAlternativesAreNotUpgrades() {
        Setup s = melee();
        s.upgrades.put(
                "WEAPON",
                Arrays.asList(
                        new Upgrade(4151, "Whip", "Best", ""),
                        new Upgrade(29796, "Halberd", "Best", "")));
        assertTrue(GearUpgrades.choices(s, "WEAPON", 4151).isEmpty());
    }

    @Test
    public void bestTooltipSaysTopOption() {
        String t =
                GearUpgrades.tooltip(
                        melee(), GuideStoreTest.snapshot(), 31097, "BOOTS", "Max boots");
        assertTrue(t.contains("Already the top option"));
        assertFalse(t.contains("Dragon boots"));
    }
}
