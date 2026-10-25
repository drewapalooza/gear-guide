package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.Gson;

import net.runelite.client.util.Filepath;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Per-account bank-only disk cache. Inventory, equipment and task always come from the live client.
 */
final class BankCache {
    private final Filepath root;
    private final Gson gson;
    private String account, lastContents;
    private Snapshot cached;

    BankCache(Filepath root, Gson gson) {
        this.root = root;
        this.gson = gson;
    }

    void apply(Snapshot next) throws IOException {
        if (!next.accountId.equals(account)) {
            account = next.accountId;
            cached = null;
            lastContents = null;
            if (!account.matches("[a-zA-Z0-9-]{1,64}"))
                throw new IOException("Invalid bank cache account ID.");
            Filepath file = root.joinSegment(account + "-bank.json");
            if (file.exists())
                try {
                    Snapshot loaded = gson.fromJson(GuideStore.read(file), Snapshot.class);
                    if (loaded != null
                            && account.equals(loaded.accountId)
                            && loaded.bankKnown
                            && loaded.bank != null
                            && loaded.bank.stream()
                                    .allMatch(i -> i != null && i.id > 0 && i.quantity >= 0))
                        cached = loaded;
                } catch (RuntimeException | IOException ignored) {
                    /* An unreadable cache must never prevent a fresh capture. */
                }
        }
        if (next.bankKnown && next.bankOpen) {
            String contents = gson.toJson(next.bank);
            // Save item changes immediately; refresh the timestamp at most once per minute.
            boolean refresh = cached == null || !contents.equals(lastContents);
            if (!refresh && next.bankCapturedAt != null && cached.bankCapturedAt != null)
                try {
                    refresh =
                            java.time.Duration.between(
                                                    java.time.Instant.parse(cached.bankCapturedAt),
                                                    java.time.Instant.parse(next.bankCapturedAt))
                                            .getSeconds()
                                    >= 60;
                } catch (RuntimeException ignored) {
                    refresh = true;
                }
            if (refresh) {
                Snapshot saved = new Snapshot();
                saved.accountId = account;
                saved.bankKnown = true;
                saved.bankCapturedAt = next.bankCapturedAt;
                saved.bank = next.bank;
                GuideStore.atomicWrite(
                        root.joinSegment(account + "-bank.json"), gson.toJson(saved));
                cached = gson.fromJson(gson.toJson(saved), Snapshot.class);
                lastContents = contents;
            }
        } else if (!next.bankKnown && cached != null) {
            next.bank = new ArrayList<>(cached.bank);
            next.bankKnown = true;
            next.bankCapturedAt = cached.bankCapturedAt;
            next.bankFromDisk = true;
        }
    }
}
