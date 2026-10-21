package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** All shared state is guarded here; HTTP workers never access the game client. */
public final class GuideStore {
    private final Path root;
    private final Gson gson;
    private final BankCache bankCache;
    private Snapshot snapshot;
    private Saved saved = new Saved();
    private String lastTaskKey = "";

    public static final class Saved {
        public String active;
        public Map<String, Setup> setups = new LinkedHashMap<>();
        public String preferredStyle = "melee";
        public boolean autoSelect = true;
    }

    public GuideStore(Path root, Gson gson) {
        this.root = root;
        this.gson = gson;
        this.bankCache = new BankCache(root, gson);
    }

    public synchronized Snapshot snapshot() {
        return snapshot;
    }

    public synchronized Saved saved() {
        return gson.fromJson(gson.toJson(saved), Saved.class);
    }

    public synchronized Setup active() {
        return saved.setups.get(saved.active);
    }

    public synchronized void clear() {
        snapshot = null;
        saved = new Saved();
        lastTaskKey = "";
    }

    public synchronized void publish(Snapshot next) throws IOException {
        bankCache.apply(next);
        if (snapshot == null || !snapshot.accountId.equals(next.accountId)) {
            Path file = root.resolve(next.accountId + "-setups.json");
            Saved loaded =
                    Files.exists(file)
                            ? gson.fromJson(Files.readString(file), Saved.class)
                            : new Saved();
            if (loaded == null || loaded.setups == null)
                throw new IOException(
                        "Invalid saved setup file; preserve it and repair before continuing.");
            saved = loaded;
            lastTaskKey = "";
            for (Setup setup : saved.setups.values()) {
                if (setup.id.equals("infernal-mages")
                        && (setup.enemyType == null || setup.enemyType.isBlank()))
                    setup.enemyType = "infernal-mages";
                if (setup.combatStyle == null) setup.combatStyle = "melee";
            }
        }
        next.revision = fingerprint(next);
        GearRequirements.enrich(next);
        snapshot = next;
        String key = next.task.known && next.task.remaining > 0 ? next.task.enemyType : "";
        if (!key.equals(lastTaskKey)) {
            if (saved.autoSelect) matchTask(false);
            lastTaskKey = key;
        }
    }

    public synchronized Setup put(Setup candidate) throws IOException {
        requireSnapshot();
        if (candidate == null) throw new IllegalArgumentException("Setup required.");
        candidate.enemyType = enemyKey(candidate.enemyType);
        if (!Objects.equals(candidate.accountId, snapshot.accountId))
            throw new IllegalArgumentException("Wrong accountId.");
        if (!Arrays.asList("melee", "ranged", "magic", "hybrid").contains(candidate.combatStyle))
            throw new IllegalArgumentException("Unknown combatStyle.");
        Setup existing = find(candidate.enemyType, candidate.combatStyle);
        Setup byId = saved.setups.get(candidate.id);
        if (existing == null
                && byId != null
                && byId.enemyType != null
                && !byId.enemyType.isBlank()) {
            if (!candidate.enemyType.isBlank()
                    && (!candidate.enemyType.equals(byId.enemyType)
                            || !candidate.combatStyle.equals(byId.combatStyle)))
                throw new IllegalArgumentException(
                        "Use a different id for a different enemy/style.");
            existing = byId;
        }
        if (existing != null && !candidate.replaceExisting) {
            GearRequirements.checkBuild(existing, snapshot);
            select(existing.id);
            return existing;
        }
        validate(candidate, snapshot);
        Setup copy = gson.fromJson(gson.toJson(candidate), Setup.class);
        copy.replaceExisting = false;
        if (existing != null) copy.id = existing.id;
        Setup sameId = saved.setups.get(copy.id);
        if (sameId != null
                && (!Objects.equals(sameId.enemyType, copy.enemyType)
                        || !Objects.equals(sameId.combatStyle, copy.combatStyle)))
            throw new IllegalArgumentException("Use a different id for a different enemy/style.");
        Saved next = saved();
        if (!next.setups.containsKey(copy.id) && next.setups.size() >= 200)
            throw new IllegalArgumentException("Maximum 200 saved setups.");
        next.setups.put(copy.id, copy);
        next.active = copy.id;
        next.preferredStyle = copy.combatStyle;
        persist(next);
        saved = next;
        return copy;
    }

    private Setup find(String enemy, String style) {
        if (enemy == null || enemy.isBlank()) return null;
        for (Setup s : saved.setups.values())
            if (enemy.equals(s.enemyType) && style.equals(s.combatStyle)) return s;
        return null;
    }

    public synchronized Map<String, Object> taskStatus() {
        SlayerTask task = snapshot == null ? new SlayerTask() : snapshot.task;
        Setup matching =
                task.known && task.remaining > 0
                        ? find(task.enemyType, saved.preferredStyle)
                        : null;
        if (matching != null && !GearRequirements.usable(matching, snapshot)) matching = null;
        return Map.of(
                "task",
                task,
                "combatStyle",
                saved.preferredStyle,
                "autoSelect",
                saved.autoSelect,
                "matchingSetupId",
                matching == null ? "" : matching.id,
                "needsBuild",
                task.known && task.remaining > 0 && matching == null);
    }

    public synchronized void preferences(String style, boolean auto) throws IOException {
        if (snapshot == null) throw new IllegalStateException("Log in first.");
        if (!Arrays.asList("melee", "ranged", "magic", "hybrid").contains(style))
            throw new IllegalArgumentException("Unknown combat style.");
        Saved next = saved();
        next.preferredStyle = style;
        next.autoSelect = auto;
        persist(next);
        saved = next;
        if (auto) matchTask(true);
    }

    public synchronized boolean matchTask(boolean force) throws IOException {
        if (snapshot == null) return false;
        SlayerTask task = snapshot.task;
        Setup found =
                task.known && task.remaining > 0
                        ? find(task.enemyType, saved.preferredStyle)
                        : null;
        if (found != null && !GearRequirements.usable(found, snapshot)) found = null;
        String target = found == null ? null : found.id;
        if (!Objects.equals(saved.active, target)) {
            Saved next = saved();
            next.active = target;
            persist(next);
            saved = next;
            return true;
        }
        return false;
    }

    public synchronized void select(String id) throws IOException {
        requireSnapshot();
        if (id != null && !saved.setups.containsKey(id))
            throw new IllegalArgumentException("Unknown setup.");
        Saved next = saved();
        next.active = id;
        if (id != null) next.preferredStyle = next.setups.get(id).combatStyle;
        persist(next);
        saved = next;
    }

    public synchronized void delete(String id) throws IOException {
        requireSnapshot();
        Saved next = saved();
        next.setups.remove(id);
        if (Objects.equals(next.active, id)) next.active = null;
        persist(next);
        saved = next;
    }

    private void requireSnapshot() {
        if (snapshot == null || !snapshot.bankKnown)
            throw new IllegalStateException("Log in and open your bank first.");
    }

    private void persist(Saved value) throws IOException {
        atomicWrite(root.resolve(snapshot.accountId + "-setups.json"), gson.toJson(value));
    }

    public static void atomicWrite(Path file, String value) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "gear-guide-", ".tmp");
        try {
            Files.writeString(temp, value, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temp,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static String hash(String value) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String fingerprint(Snapshot s) {
        // Moving gear between bank/inventory/equipment must not invalidate a recommendation.
        Map<Integer, Long> physical = new TreeMap<>();
        for (OwnedItem i : s.all()) physical.merge(i.id, i.quantity, Long::sum);
        Map<Integer, Long> dosePools = new TreeMap<>();
        if (s.potionStorageKnown)
            for (PotionStock p : s.potionStorage) {
                long doses = p.totalDoses;
                for (Map.Entry<Integer, OwnedItem> v : p.variants.entrySet()) {
                    Long bottles = physical.remove(v.getValue().id);
                    if (bottles != null) doses += bottles * v.getKey();
                }
                dosePools.put(p.enumId, doses);
            }
        return hash(
                s.accountId
                        + ":"
                        + physical
                        + ":"
                        + s.potionStorageKnown
                        + ":"
                        + dosePools
                        + ":"
                        + s.levels);
    }

    public static void validate(Setup s, Snapshot bank) {
        if (s == null || s.schemaVersion != 1) fail("Expected schemaVersion 1.");
        GearUpgrades.validate(s);
        if (!Objects.equals(s.accountId, bank.accountId)) fail("Wrong accountId.");
        if (!Objects.equals(s.basedOnRevision, bank.revision))
            fail("Bank or levels changed. Fetch a fresh snapshot before sending the setup.");
        if (s.id == null || !s.id.matches("[a-z0-9][a-z0-9-]{0,63}"))
            fail("id must use 1-64 lowercase letters, digits or hyphens.");
        if (s.name == null || s.name.isBlank() || s.name.length() > 100)
            fail("name must be 1-100 characters.");
        if (s.notes == null || s.notes.length() > 8000)
            fail("notes must be at most 8000 characters.");
        if (s.equipment == null || s.inventory == null || s.inventory.size() > 28)
            fail("Invalid equipment or inventory.");
        if (s.runes == null || s.runes.size() > RuneSupplies.MAX_TYPES)
            fail("Invalid rune supplies (maximum 32 types).");
        if (s.equipment.isEmpty() && s.inventory.isEmpty()) fail("Setup is empty.");
        Map<Integer, OwnedItem> catalog = new HashMap<>();
        for (OwnedItem i : bank.catalog()) catalog.put(i.id, i);
        Map<Integer, Long> needed = new HashMap<>();
        for (Map.Entry<String, Pick> e : s.equipment.entrySet()) {
            Integer slot = SLOTS.get(e.getKey());
            if (slot == null) fail("Unknown equipment slot: " + e.getKey());
            OwnedItem i = checkPick(e.getValue(), catalog, needed);
            GearRequirements.check(i, bank);
            if (!slot.equals(i.equipmentSlot)) fail(i.name + " cannot be worn in " + e.getKey());
            if (slot != 13 && e.getValue().quantity != 1)
                fail("Equipment quantity must be 1 except AMMO.");
        }
        Pick weapon = s.equipment.get("WEAPON");
        if (weapon != null
                && catalog.get(weapon.itemId).twoHanded
                && s.equipment.containsKey("SHIELD"))
            fail("Two-handed weapon conflicts with shield.");
        Set<Integer> slots = new HashSet<>();
        for (InventoryPick p : s.inventory) {
            if (p == null || p.slot < 0 || p.slot > 27 || !slots.add(p.slot))
                fail("Inventory slots must be unique, 0-27.");
            OwnedItem i = checkPick(p.item, catalog, needed);
            GearRequirements.check(i, bank);
            if (!i.stackable && p.item.quantity > 1)
                fail("Use separate inventory slots for non-stackable " + i.name);
        }
        Set<Integer> runeIds = new HashSet<>();
        for (Pick rune : s.runes) {
            OwnedItem item = checkPick(rune, catalog, needed);
            if (!RuneSupplies.isRune(item)) fail("Rune supplies must contain unnoted runes.");
            if (!runeIds.add(rune.itemId)) fail("Rune supplies must use unique rune types.");
        }
        for (Map.Entry<Integer, Long> n : bank.shortages(needed).entrySet())
            fail(
                    "Not enough "
                            + catalog.get(n.getKey()).name
                            + ": short "
                            + n.getValue()
                            + ". Potion dose variants share the same storage supply.");
    }

    private static OwnedItem checkPick(
            Pick p, Map<Integer, OwnedItem> catalog, Map<Integer, Long> needed) {
        if (p == null
                || p.itemId <= 0
                || p.quantity < 1
                || p.quantity > Integer.MAX_VALUE
                || p.note == null
                || p.note.length() > 1000) fail("Invalid item or quantity.");
        OwnedItem i = catalog.get(p.itemId);
        if (i == null)
            fail("Item " + p.itemId + " is not in the observed bank, inventory or equipment.");
        needed.merge(p.itemId, p.quantity, Long::sum);
        return i;
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
