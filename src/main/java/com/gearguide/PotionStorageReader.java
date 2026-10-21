package com.gearguide;

import static com.gearguide.Models.*;

import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

import java.util.*;

/** Reads the same query scripts/enums used by RuneLite Bank Tags. Client thread only. */
final class PotionStorageReader {
    static List<PotionStock> read(Client client, ItemManager items) {
        List<PotionStock> result = new ArrayList<>();
        for (int enumId :
                new int[] {EnumID.POTIONSTORE_POTIONS, EnumID.POTIONSTORE_UNFINISHED_POTIONS}) {
            EnumComposition list = client.getEnum(enumId);
            if (list == null)
                throw new IllegalStateException("Potion storage definitions unavailable.");
            for (int familyId : list.getIntVals()) {
                EnumComposition family = client.getEnum(familyId);
                client.runScript(ScriptID.POTIONSTORE_DOSES, familyId);
                int total = client.getIntStack()[0];
                client.runScript(ScriptID.POTIONSTORE_WITHDRAW_DOSES, familyId);
                int withdraw = client.getIntStack()[0];
                if (total < 0) throw new IllegalStateException("Invalid potion dose count.");
                PotionStock stock = new PotionStock();
                stock.enumId = familyId;
                stock.totalDoses = total;
                stock.withdrawDoses = withdraw;
                int[] keys = family.getKeys(), ids = family.getIntVals();
                for (int i = 0; i < keys.length; i++) {
                    int dose = keys[i], id = ids[i];
                    if (dose < 1 || dose > 4 || id <= 0) continue;
                    OwnedItem item = new OwnedItem();
                    item.id = id;
                    item.rawId = id;
                    item.slot = -1;
                    item.name = items.getItemComposition(id).getName();
                    item.quantity = total / dose;
                    stock.variants.put(dose, item);
                }
                result.add(stock);
            }
        }
        return result;
    }
}
