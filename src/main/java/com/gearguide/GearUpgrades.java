package com.gearguide;

import static com.gearguide.Models.*;

import java.util.*;

final class GearUpgrades {
    static List<Upgrade> choices(Setup build, String slot, int selected) {
        List<Upgrade> ladder = ladder(build, slot, selected);
        int current = selectedRank(ladder, selected);
        if (current < 0) return Collections.emptyList();
        List<Upgrade> higher = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Upgrade u : ladder)
            if (rank(u.tier) > current
                    && canonical(u.itemId) != canonical(selected)
                    && seen.add(canonical(u.itemId))) higher.add(u);
        higher.sort(Comparator.comparingInt(u -> rank(u.tier)));
        return higher;
    }

    private static int rank(String tier) {
        return "Good".equals(tier) ? 0 : "Better".equals(tier) ? 1 : "Best".equals(tier) ? 2 : -1;
    }

    private static int canonical(int id) {
        if (id == 29804) return 29801;
        if (Arrays.asList(22486, 25736, 25739, 25738, 25741).contains(id)) return 22325;
        return id;
    }

    private static int selectedRank(List<Upgrade> ladder, int selected) {
        int result = -1;
        for (Upgrade u : ladder)
            if (canonical(u.itemId) == canonical(selected)) result = Math.max(result, rank(u.tier));
        return result;
    }

    private static List<Upgrade> ladder(Setup build, String slot, int selected) {
        if (build == null) return Collections.emptyList();
        String enemy = enemyKey(build.enemyType);
        if (slot.equals("WEAPON") && (enemy.equals("duke-sucellus") || enemy.equals("duke")))
            return Arrays.asList(
                    new Upgrade(19675, "Arclight", "Good", "Demonbane; charges required."),
                    new Upgrade(
                            29589,
                            "Emberlight",
                            "Better",
                            "Demonbane upgrade for Duke; check creation unlocks."),
                    new Upgrade(
                            22325,
                            "Scythe of vitur",
                            "Best",
                            "Duke-specific ranking; two-handed, charged weapon. Remove your"
                                    + " defender."));
        if (build.upgrades != null && build.upgrades.containsKey(slot))
            return build.upgrades.get(slot);
        if (Arrays.asList(6585, 19553, 29801, 29804).contains(selected)
                && slot.equals("AMULET")
                && build.combatStyle.equals("melee"))
            return Arrays.asList(
                    new Upgrade(6585, "Amulet of fury", "Good", "Balanced defence and prayer."),
                    new Upgrade(19553, "Amulet of torture", "Better", "Melee damage priority."),
                    new Upgrade(
                            29801,
                            "Amulet of rancour",
                            "Best",
                            "Melee damage priority; not a prayer upgrade."));
        if (Arrays.asList(11840, 13239, 31097).contains(selected)
                && slot.equals("BOOTS")
                && build.combatStyle.equals("melee")
                && !enemy.equals("wyrms")
                && !enemy.equals("drakes"))
            return Arrays.asList(
                    new Upgrade(11840, "Dragon boots", "Good", "General melee damage."),
                    new Upgrade(13239, "Primordial boots", "Better", "General melee damage."),
                    new Upgrade(
                            31097,
                            "Avernic treads (max)",
                            "Best",
                            "General melee damage; special protective boots can take priority."));
        return Collections.emptyList();
    }

    static String status(Upgrade u, Snapshot s, int selected) {
        List<String> status = new ArrayList<>();
        boolean owned = s != null && s.counts().getOrDefault(u.itemId, 0L) > 0;
        boolean variant =
                s != null
                        && u.itemId == 22325
                        && !owned
                        && s.all().stream()
                                .anyMatch(
                                        i ->
                                                Arrays.asList(22486, 25736, 25739, 25738, 25741)
                                                        .contains(i.id));
        status.add(
                u.itemId == selected
                        ? "Selected"
                        : owned
                                ? "Owned, not selected"
                                : variant ? "Owned variant; check charges" : "Not owned");
        String blocked = GearRequirements.blocked(u.itemId, s);
        status.add(blocked.isEmpty() ? "Levels met" : "Blocked: " + blocked);
        if (s != null && !s.bankOpen) status.add("ownership uses cached bank");
        return String.join("; ", status);
    }

    static String tooltip(Setup build, Snapshot s, int id, String slot, String base) {
        StringBuilder out =
                new StringBuilder("<html><body style='width:300px'>").append(escape(base));
        if (s != null) {
            String block = GearRequirements.blocked(id, s);
            out.append("<br><b>")
                    .append(block.isEmpty() ? "Levels met" : escape(block))
                    .append("</b>");
        }
        List<Upgrade> options = choices(build, slot, id);
        if (!options.isEmpty()) {
            out.append("<hr><b>Upgrade options</b>");
            if (build.upgrades != null
                    && build.upgrades.containsKey(slot)
                    && !enemyKey(build.enemyType).startsWith("duke"))
                out.append("<br>Build-author suggestions; not a DPS calculation.");
            for (Upgrade u : options) {
                out.append("<br><br><b>")
                        .append(escape(u.tier))
                        .append(": ")
                        .append(escape(u.name))
                        .append("</b><br>")
                        .append(escape(status(u, s, id)))
                        .append("<br>")
                        .append(escape(u.reason));
            }
            out.append("<br><br>Quest, diary and charge requirements are not verified.");
        } else if (selectedRank(ladder(build, slot, id), id) == 2)
            out.append("<br>Already the top option in this guide.");
        else if (selectedRank(ladder(build, slot, id), id) >= 0)
            out.append("<br>No higher-ranked upgrade in this guide.");
        else out.append("<br>No verified upgrade comparison for this item/task yet.");
        return out.append("</body></html>").toString();
    }

    static String escape(String s) {
        return s == null
                ? ""
                : s.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }

    static void validate(Setup s) {
        if (s.upgrades == null) return;
        if (s.upgrades.size() > 11) throw new IllegalArgumentException("Too many upgrade groups.");
        for (Map.Entry<String, List<Upgrade>> e : s.upgrades.entrySet()) {
            if (!SLOTS.containsKey(e.getKey()) || e.getValue() == null || e.getValue().size() > 3)
                throw new IllegalArgumentException("Invalid upgrade slot or list.");
            for (Upgrade u : e.getValue())
                if (u == null
                        || u.itemId <= 0
                        || u.name == null
                        || u.name.length() > 100
                        || u.reason == null
                        || u.reason.length() > 600
                        || !Arrays.asList("Good", "Better", "Best").contains(u.tier))
                    throw new IllegalArgumentException("Invalid upgrade suggestion.");
        }
    }
}
