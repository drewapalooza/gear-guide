package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local requirements, never requirements invented by an AI response. */
final class GearRequirements {
    private static final Map<Integer, Map<String, Integer>> DATA = new HashMap<>();

    static {
        try (InputStream in =
                GearRequirements.class.getResourceAsStream("item-requirements.json")) {
            if (in == null) throw new IOException("Missing requirement data");
            JsonObject root =
                    new JsonParser()
                            .parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet())
                if (entry.getValue().isJsonObject()) {
                    Map<String, Integer> req = new TreeMap<>();
                    for (Map.Entry<String, JsonElement> v :
                            entry.getValue().getAsJsonObject().entrySet())
                        req.put(v.getKey().toUpperCase(Locale.ROOT), v.getValue().getAsInt());
                    DATA.put(Integer.parseInt(entry.getKey()), Collections.unmodifiableMap(req));
                }
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
        // Ornament kits change appearance, not the underlying Void equipment requirements.
        int[][] voidVariants = {
            {26463, 8839},
            {26465, 8840},
            {26467, 8842},
            {26469, 13072},
            {26471, 13073},
            {26473, 11663},
            {26475, 11664},
            {26477, 11665}
        };
        for (int[] variant : voidVariants)
            DATA.put(
                    variant[0],
                    Objects.requireNonNull(DATA.get(variant[1]), "Missing base Void requirements"));
        // Rebalanced requirements and newer items missing from the baseline dataset.
        put(new int[] {22325, 22486, 25736, 25739, 25738, 25741}, "ATTACK", 80, "STRENGTH", 90);
        put(new int[] {29589}, "ATTACK", 77);
        put(new int[] {29796}, "ATTACK", 80);
        put(new int[] {26219}, "ATTACK", 82);
        put(new int[] {22978}, "ATTACK", 78);
        put(new int[] {22324, 23995, 23997, 24417}, "ATTACK", 80);
        put(new int[] {25865, 25867}, "RANGED", 80, "AGILITY", 70);
        put(new int[] {27275, 27277}, "MAGIC", 85);
        put(new int[] {27235, 27238, 27241}, "RANGED", 80, "DEFENCE", 70);
        put(new int[] {28933, 28936, 28939}, "DEFENCE", 40, "PRAYER", 60);
        put(new int[] {31097}, "DEFENCE", 80, "STRENGTH", 80, "RANGED", 80, "MAGIC", 80);
        put(new int[] {29801, 29804}, "HITPOINTS", 90);
        put(new int[] {26382, 26384, 26386}, "DEFENCE", 80);
        put(new int[] {21006}, "MAGIC", 80);
        put(new int[] {22323}, "MAGIC", 82);
        put(
                new int[] {
                    6570, 21295, 21268, 11773, 13202, 22945, 22947, 6585, 24780, 28307, 28313, 28316
                },
                new Object[] {});
    }

    private static void put(int[] ids, Object... pairs) {
        Map<String, Integer> req = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            req.put((String) pairs[i], (Integer) pairs[i + 1]);
        for (int id : ids) DATA.put(id, Collections.unmodifiableMap(req));
    }

    static Map<String, Integer> requirements(int id) {
        return DATA.get(id);
    }

    static String blocked(int id, Snapshot s) {
        Map<String, Integer> req = DATA.get(id);
        if (req == null) {
            if (s != null && s.equipment.stream().anyMatch(i -> i.id == id)) return "";
            return "Level requirements not verified";
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> r : req.entrySet()) {
            Integer actual = s == null ? null : s.levels.get(r.getKey());
            if (actual == null) missing.add(r.getKey() + " " + r.getValue() + " (level unknown)");
            else if (actual < r.getValue())
                missing.add(r.getKey() + " " + r.getValue() + " (you: " + actual + ")");
        }
        return String.join(", ", missing);
    }

    static void enrich(Snapshot s) {
        for (OwnedItem item : s.catalog())
            if (item.equipmentSlot != null) {
                Map<String, Integer> req = DATA.get(item.id);
                item.levelRequirements = req;
                item.levelRequirementsKnown = req != null;
                item.levelBlock = blocked(item.id, s);
            }
    }

    static void check(OwnedItem item, Snapshot s) {
        if (item.equipmentSlot != null) {
            String reason = blocked(item.id, s);
            if (!reason.isEmpty())
                throw new IllegalArgumentException(
                        item.name + ": " + reason + ". Choose a verified usable item.");
        }
    }

    static void checkBuild(Setup build, Snapshot s) {
        for (Pick pick : build.equipment.values()) {
            String reason = blocked(pick.itemId, s);
            if (!reason.isEmpty())
                throw new IllegalArgumentException(
                        "Saved build item " + pick.itemId + ": " + reason + ". Revise this build.");
        }
        for (InventoryPick pick : build.inventory)
            for (OwnedItem item : s.catalog())
                if (item.id == pick.item.itemId) {
                    check(item, s);
                    break;
                }
    }

    static boolean usable(Setup build, Snapshot s) {
        try {
            checkBuild(build, s);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
