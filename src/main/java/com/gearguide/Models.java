package com.gearguide;

import com.google.gson.JsonObject;

import java.util.*;

public final class Models {
    private Models() {}

    public static final Map<String, Integer> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put("HEAD", 0);
        SLOTS.put("CAPE", 1);
        SLOTS.put("AMULET", 2);
        SLOTS.put("WEAPON", 3);
        SLOTS.put("BODY", 4);
        SLOTS.put("SHIELD", 5);
        SLOTS.put("LEGS", 7);
        SLOTS.put("GLOVES", 9);
        SLOTS.put("BOOTS", 10);
        SLOTS.put("RING", 12);
        SLOTS.put("AMMO", 13);
    }

    public static final class OwnedItem {
        public int id, rawId, slot;
        public long quantity;
        public String name;
        public boolean stackable, twoHanded, noted;
        public Integer equipmentSlot;
        public JsonObject stats;
        public Map<String, Integer> levelRequirements;
        public boolean levelRequirementsKnown;
        public String levelBlock = "";
    }

    public static final class Snapshot {
        public int schemaVersion = 1;
        public String accountId, revision, capturedAt, bankCapturedAt;
        public boolean bankOpen, bankKnown;
        public boolean bankFromDisk;
        public SlayerTask task = new SlayerTask();
        public boolean potionStorageKnown;
        public String potionStorageCapturedAt;
        public List<PotionStock> potionStorage = new ArrayList<>();
        public Map<String, Integer> levels = new TreeMap<>();
        public List<OwnedItem> bank = new ArrayList<>(),
                inventory = new ArrayList<>(),
                equipment = new ArrayList<>();
        public String scope =
                "Personal bank, current inventory/equipment, and potion storage after its tab has"
                    + " been opened this session. Potion variants share totalDoses; their possible"
                    + " quantities must not be added together. Excludes other storage, item"
                    + " charges, quests and unlocks.";

        public List<OwnedItem> all() {
            List<OwnedItem> all = new ArrayList<>(bank);
            all.addAll(inventory);
            all.addAll(equipment);
            return all;
        }

        public Map<Integer, Long> counts() {
            Map<Integer, Long> result = new TreeMap<>();
            for (OwnedItem i : all()) result.merge(i.id, i.quantity, Long::sum);
            if (potionStorageKnown)
                for (PotionStock p : potionStorage)
                    for (Map.Entry<Integer, OwnedItem> v : p.variants.entrySet())
                        result.merge(v.getValue().id, p.totalDoses / v.getKey(), Long::sum);
            return result;
        }

        public List<OwnedItem> catalog() {
            List<OwnedItem> result = all();
            if (potionStorageKnown)
                for (PotionStock p : potionStorage) result.addAll(p.variants.values());
            return result;
        }

        /** Allocate physical bottles first, then one shared dose pool per potion family. */
        public Map<Integer, Long> shortages(Map<Integer, Long> needed) {
            Map<Integer, Long> physical = new HashMap<>();
            for (OwnedItem i : all()) physical.merge(i.id, i.quantity, Long::sum);
            Map<Integer, PotionStock> families = new HashMap<>();
            Map<Integer, Integer> sizes = new HashMap<>();
            Map<Integer, Long> remaining = new HashMap<>();
            if (potionStorageKnown)
                for (PotionStock p : potionStorage) {
                    remaining.put(p.enumId, p.totalDoses);
                    for (Map.Entry<Integer, OwnedItem> v : p.variants.entrySet()) {
                        families.put(v.getValue().id, p);
                        sizes.put(v.getValue().id, v.getKey());
                    }
                }
            Map<Integer, Long> missing = new LinkedHashMap<>();
            for (Map.Entry<Integer, Long> n : new TreeMap<>(needed).entrySet()) {
                long deficit = Math.max(0, n.getValue() - physical.getOrDefault(n.getKey(), 0L));
                PotionStock p = families.get(n.getKey());
                if (p != null && deficit > 0) {
                    int size = sizes.get(n.getKey());
                    long available = remaining.get(p.enumId);
                    long used = Math.min(deficit, available / size);
                    remaining.put(p.enumId, available - used * size);
                    deficit -= used;
                }
                if (deficit > 0) missing.put(n.getKey(), deficit);
            }
            return missing;
        }
    }

    public static final class PotionStock {
        public int enumId, withdrawDoses;
        public long totalDoses;
        public Map<Integer, OwnedItem> variants = new TreeMap<>();
    }

    public static final class Pick {
        public int itemId;
        public long quantity = 1;
        public String note = "";

        public Pick() {}

        public Pick(int id, long quantity) {
            this.itemId = id;
            this.quantity = quantity;
        }
    }

    public static final class InventoryPick {
        public int slot;
        public Pick item;
    }

    public static final class Setup {
        public int schemaVersion = 1;
        public String id, accountId, basedOnRevision, name, notes = "";
        public String enemyType = "", combatStyle = "melee";
        public boolean replaceExisting;
        public Map<String, Pick> equipment = new LinkedHashMap<>();
        public List<InventoryPick> inventory = new ArrayList<>();
        public Map<String, List<Upgrade>> upgrades = new LinkedHashMap<>();

        @Override
        public String toString() {
            return name;
        }

        public List<Pick> picks() {
            List<Pick> out = new ArrayList<>(equipment.values());
            for (InventoryPick p : inventory) out.add(p.item);
            return out;
        }
    }

    public static final class SlayerTask {
        public boolean known;
        public String enemyType = "", name = "", location = "";
        public int remaining;
    }

    public static final class Upgrade {
        public int itemId;
        public String name = "", tier = "", reason = "";

        public Upgrade() {}

        public Upgrade(int id, String name, String tier, String reason) {
            this.itemId = id;
            this.name = name;
            this.tier = tier;
            this.reason = reason;
        }
    }

    public static String enemyKey(String name) {
        return name == null
                ? ""
                : name.trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", "");
    }
}
