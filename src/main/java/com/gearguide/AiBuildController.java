package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.Gson;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

import javax.swing.*;

/** UI actions run on Swing; provider calls run only in a cancellable background worker. */
final class AiBuildController implements AutoCloseable {
    static final class Settings {
        AiBuildClient.Provider provider = AiBuildClient.Provider.GPT;
        Map<String, String> models = new HashMap<>();
    }

    private final GuideStore store;
    private final GuidePanel panel;
    private final Runnable changed;
    private final Path settingsFile;
    private final Gson gson = new Gson();
    private final Map<AiBuildClient.Provider, String> keys =
            new EnumMap<>(AiBuildClient.Provider.class);
    private Settings settings = new Settings();
    private SwingWorker<Setup, Void> worker;
    private boolean closed;

    AiBuildController(GuideStore store, GuidePanel panel, Path root, Runnable changed) {
        this.store = store;
        this.panel = panel;
        this.changed = changed;
        settingsFile = root.resolve("ai-settings.json");
        try {
            if (Files.exists(settingsFile)) {
                Settings loaded = gson.fromJson(Files.readString(settingsFile), Settings.class);
                if (loaded != null && loaded.provider != null && loaded.models != null)
                    settings = loaded;
            }
        } catch (Exception ignored) {
        }
    }

    void configure() {
        JComboBox<AiBuildClient.Provider> providers =
                new JComboBox<>(AiBuildClient.Provider.values());
        providers.setSelectedItem(settings.provider);
        JTextField model =
                new JTextField(settings.models.getOrDefault(settings.provider.name(), ""), 28);
        JPasswordField key = new JPasswordField(keys.getOrDefault(settings.provider, ""), 28);
        final AiBuildClient.Provider[] current = {settings.provider};
        Map<AiBuildClient.Provider, String> draftKeys = new EnumMap<>(keys);
        Map<String, String> draftModels = new HashMap<>(settings.models);
        providers.addActionListener(
                e -> {
                    draftKeys.put(current[0], new String(key.getPassword()));
                    draftModels.put(current[0].name(), model.getText().trim());
                    current[0] = (AiBuildClient.Provider) providers.getSelectedItem();
                    model.setText(draftModels.getOrDefault(current[0].name(), ""));
                    key.setText(draftKeys.getOrDefault(current[0], ""));
                });
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(new JLabel("Provider"));
        form.add(providers);
        form.add(new JLabel("Exact model ID from your provider (text/chat model)"));
        form.add(model);
        form.add(new JLabel("API key - this session only; blank clears it"));
        form.add(key);
        form.add(new JLabel("Optional. Chat builds still work with no key."));
        form.add(new JLabel("Requests send game data to this provider and may incur charges."));
        if (JOptionPane.showConfirmDialog(
                        panel,
                        form,
                        "AI settings",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return;
        draftKeys.put(current[0], new String(key.getPassword()));
        draftModels.put(current[0].name(), model.getText().trim());
        Settings next = new Settings();
        next.provider = current[0];
        next.models = draftModels;
        try {
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, gson.toJson(next));
            settings = next;
            keys.clear();
            keys.putAll(draftKeys);
        } catch (Exception ex) {
            panel.error("Could not save AI settings.");
        }
        key.setText("");
    }

    void request() {
        if (worker != null) {
            panel.error("A build request is already running. Cancel it before starting another.");
            return;
        }
        Snapshot live = store.snapshot();
        if (live == null || !live.bankKnown) {
            panel.error("Open your bank once to save its inventory before requesting a build.");
            return;
        }
        if (!live.task.known || live.task.remaining <= 0) {
            panel.error("Accept a Slayer task first, then check task.");
            return;
        }
        Snapshot captured = gson.fromJson(gson.toJson(live), Snapshot.class);
        String style = store.saved().preferredStyle;
        Setup existing = matching(style, captured.task.enemyType);
        if (existing != null) {
            Object[] choices = {"Use saved build", "Generate replacement", "Cancel"};
            int choice =
                    JOptionPane.showOptionDialog(
                            panel,
                            "You already have a saved build for this enemy and style.",
                            "Saved build found",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            choices,
                            choices[0]);
            if (choice == 0) {
                try {
                    GearRequirements.checkBuild(existing, captured);
                    store.select(existing.id);
                    changed.run();
                } catch (Exception ex) {
                    panel.error(ex.getMessage());
                }
                return;
            }
            if (choice != 1) return;
        }
        AiBuildClient.Provider provider = settings.provider;
        String model = settings.models.getOrDefault(provider.name(), "");
        String key = keys.getOrDefault(provider, "");
        if (model.isBlank() || key.isBlank()) {
            configure();
            return;
        }
        JTextArea preferences =
                new JTextArea(
                        "Prefer relaxed Slayer trips. Include goading potion where useful.", 3, 36);
        preferences.setLineWrap(true);
        preferences.setWrapStyleWord(true);
        if (!captured.bankOpen)
            preferences.append(
                    "\nUsing a cached bank from "
                            + captured.bankCapturedAt
                            + ". Supplies may have changed; flag uncertain quantities.");
        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.add(
                new JLabel(
                        "<html>Send task, bank, equipment, levels and known potion storage to<br>"
                                + provider
                                + "? Provider usage charges may apply.<br>Model: "
                                + escape(model)
                                + "<br>Review the result before saving.</html>"),
                BorderLayout.NORTH);
        form.add(new JScrollPane(preferences), BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(
                        panel,
                        form,
                        "Request AI build",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return;
        String preference = preferences.getText();
        String original = existing == null ? null : gson.toJson(existing);
        panel.aiStatus("Requesting build...");
        panel.aiBusy(true);
        worker =
                new SwingWorker<Setup, Void>() {
                    protected Setup doInBackground() throws Exception {
                        return new AiBuildClient()
                                .generate(provider, model, key, captured, style, preference);
                    }

                    protected void done() {
                        if (worker != this) return;
                        worker = null;
                        panel.aiBusy(false);
                        if (closed) return;
                        panel.aiStatus("Ready");
                        try {
                            Setup result = get();
                            if (!stillCurrent(captured, style)) {
                                panel.error(
                                        "Your bank, account, task or style changed. Request a fresh"
                                            + " build.");
                                return;
                            }
                            SetupEditor.open(
                                    panel,
                                    captured,
                                    result,
                                    draft -> {
                                        try {
                                            if (!stillCurrent(captured, style))
                                                throw new IllegalArgumentException(
                                                        "Your bank, account, task or style changed."
                                                            + " Request a fresh build.");
                                            Setup now = matching(style, captured.task.enemyType);
                                            if (!Objects.equals(
                                                    original,
                                                    now == null ? null : gson.toJson(now)))
                                                throw new IllegalArgumentException(
                                                        "This saved build changed while you were"
                                                            + " reviewing. Request again to"
                                                            + " preserve those edits.");
                                            store.put(draft);
                                            changed.run();
                                            return true;
                                        } catch (Exception ex) {
                                            panel.error(ex.getMessage());
                                            return false;
                                        }
                                    });
                        } catch (CancellationException ex) {
                            panel.aiStatus("Cancelled");
                        } catch (Exception ex) {
                            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                            panel.error(
                                    cause instanceof IllegalArgumentException
                                            ? cause.getMessage()
                                            : "Could not reach provider or request timed out. Check"
                                                  + " connection and AI settings. No build was"
                                                  + " saved.");
                        }
                    }
                };
        worker.execute();
    }

    private Setup matching(String style, String enemy) {
        for (Setup s : store.saved().setups.values())
            if (enemy.equals(s.enemyType) && style.equals(s.combatStyle)) return s;
        return null;
    }

    private boolean stillCurrent(Snapshot captured, String style) {
        Snapshot now = store.snapshot();
        return now != null
                && Objects.equals(now.accountId, captured.accountId)
                && Objects.equals(now.revision, captured.revision)
                && now.task.known
                && now.task.remaining > 0
                && Objects.equals(now.task.enemyType, captured.task.enemyType)
                && style.equals(store.saved().preferredStyle);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    void cancel() {
        if (worker != null) {
            worker.cancel(true);
            worker = null;
            panel.aiBusy(false);
            panel.aiStatus("Cancelled");
        }
    }

    public void close() {
        closed = true;
        cancel();
        keys.clear();
    }
}
