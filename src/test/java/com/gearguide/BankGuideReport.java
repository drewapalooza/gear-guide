package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.Gson;

import java.nio.file.*;
import java.util.*;

/** Offline audit, no connection to the game and no assignments are changed. */
public final class BankGuideReport {
    public static void main(String[] args) throws Exception {
        Snapshot s = new Gson().fromJson(Files.readString(Path.of(args[0])), Snapshot.class);
        List<String> lines = new ArrayList<>();
        lines.add("Tab,Category,Item,Needs review");
        int[] counts = new int[9];
        int reviews = 0;
        for (OwnedItem i : s.bank) {
            BankOrganizer.Choice c = BankOrganizer.choose(i, Collections.emptyMap());
            counts[c.tab - 1]++;
            if (c.review) {
                reviews++;
                System.out.println("Review: " + i.name);
            }
            lines.add(
                    c.tab
                            + ",\""
                            + BankOrganizer.NAMES[c.tab - 1]
                            + "\",\""
                            + i.name.replace("\"", "\"\"")
                            + "\","
                            + c.review);
        }
        Files.write(Path.of(args[1]), lines);
        System.out.println(
                "Categories: "
                        + Arrays.toString(counts)
                        + "; review: "
                        + reviews
                        + "; total: "
                        + s.bank.size());
    }
}
