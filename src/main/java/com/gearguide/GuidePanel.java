package com.gearguide;

import static com.gearguide.Models.*;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

import java.awt.*;
import java.util.*;

import javax.swing.*;
import javax.swing.border.*;

public final class GuidePanel extends PluginPanel {
    public interface Actions {
        void select(String id);

        void delete(String id);

        void filter();

        void unfilter();

        void exportSnapshot();

        void capture();

        default boolean save(Setup setup) {
            return false;
        }

        default void pollTask() {}

        default void aiSettings() {}

        default void port(int value) {}

        default int connectionPort() {
            return 17654;
        }

        default boolean assistantConnectionEnabled() {
            return false;
        }

        default void assistantConnectionEnabled(boolean enabled) {}

        default void bankGuide(boolean active) {}

        default void groupBank() {}

        default boolean bankGuideEnabled() {
            return true;
        }

        default void bankGuideEnabled(boolean enabled) {}

        default void assignBank(String account, int item, int tab) {}

        default void requestBuild() {}

        default void cancelBuild() {}

        default void preferences(String style, boolean auto) {}
    }

    private static final Color BG = new Color(25, 29, 34),
            TILE = new Color(37, 43, 50),
            MUTED = new Color(155, 167, 177);
    private static final Color GREEN = new Color(63, 205, 172),
            AMBER = new Color(234, 181, 79),
            RED = new Color(238, 111, 112);
    private final ItemManager items;
    private final Actions actions;
    private final JLabel status = new JLabel(), freshness = new JLabel(), potions = new JLabel();
    private final JComboBox<Setup> choices = new JComboBox<>();
    private final JPanel content = column();
    private boolean updating;
    private Snapshot snapshot;
    private Setup activeSetup;
    private final JLabel taskLabel = new JLabel();
    private final JComboBox<String> taskStyle =
            new JComboBox<>(new String[] {"melee", "ranged", "magic", "hybrid"});
    private final JCheckBox autoTask = new JCheckBox("Auto-select task build", true);
    private final JLabel aiState = new JLabel("AI optional - configure a provider to enable");
    private final JProgressBar aiProgress = new JProgressBar();
    private JButton requestAi, cancelAi;

    public void aiStatus(String text) {
        aiState.setText(text);
        aiState.setVisible(!text.equals("Ready"));
    }

    public void aiBusy(boolean busy) {
        aiProgress.setIndeterminate(busy);
        aiProgress.setVisible(busy);
        requestAi.setEnabled(!busy);
        requestAi.setToolTipText(
                busy ? "Generating build..." : "Request a build from your AI provider");
        cancelAi.setEnabled(busy);
        revalidate();
        repaint();
    }

    private final JEditorPane notes = new JEditorPane("text/html", "");
    private final BankGuidePanel bankGuide;

    void bankGuideUpdate(Snapshot s, Map<Integer, Integer> overrides) {
        bankGuide.update(s, overrides);
    }

    void previewBank() {
        tabs.select("bank");
    }

    private final GuideNavigation tabs;
    private GuideStore.Saved latestSaved;
    private java.util.List<String> lastChoices = Collections.emptyList();
    private int apiPort;
    private String lastNotes;
    private final com.google.gson.Gson gson;

    public GuidePanel(com.google.gson.Gson gson, ItemManager items, Actions actions) {
        this.gson = gson;
        this.items = items;
        this.actions = actions;
        bankGuide = new BankGuidePanel(actions::assignBank, actions::groupBank, actions::unfilter);
        tabs = new GuideNavigation(key -> actions.bankGuide("bank".equals(key)));
        setLayout(new BorderLayout(0, 10));
        setBackground(BG);
        setBorder(new EmptyBorder(12, 10, 10, 10));
        JPanel header = column();
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = new JLabel("Ayra's Gear Guide");
        title.setForeground(GREEN);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleRow.add(title, BorderLayout.CENTER);
        titleRow.add(
                iconButton(
                        "settings",
                        "Settings: task matching, AI provider and connection",
                        this::showSettings),
                BorderLayout.EAST);
        titleRow.setMaximumSize(new Dimension(250, 32));
        header.add(titleRow);
        taskLabel.setForeground(Color.WHITE);
        header.add(Box.createVerticalStrut(8));
        header.add(taskLabel);
        freshness.setForeground(MUTED);
        freshness.setFont(new Font("SansSerif", Font.PLAIN, 11));
        header.add(freshness);
        header.add(Box.createVerticalStrut(10));
        choices.setMaximumSize(new Dimension(240, 28));
        header.add(choices);
        choices.addActionListener(
                e -> {
                    if (!updating && choices.getSelectedItem() != null)
                        actions.select(((Setup) choices.getSelectedItem()).id);
                });
        choices.addPopupMenuListener(
                new javax.swing.event.PopupMenuListener() {
                    public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}

                    public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                        SwingUtilities.invokeLater(() -> syncChoices(latestSaved));
                    }

                    public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                        SwingUtilities.invokeLater(() -> syncChoices(latestSaved));
                    }
                });
        JPanel tools = new JPanel(new GridLayout(1, 6, 3, 0));
        tools.setOpaque(false);
        tools.setMaximumSize(new Dimension(250, 32));
        tools.add(
                iconButton("refresh", "Check task and select its saved build", actions::pollTask));
        requestAi =
                iconButton("ai", "Request a build from your AI provider", actions::requestBuild);
        tools.add(requestAi);
        cancelAi = iconButton("stop", "Cancel the current AI request", actions::cancelBuild);
        cancelAi.setEnabled(false);
        tools.add(cancelAi);
        tools.add(iconButton("copy", "Copy a build request to paste into chat", this::copyRequest));
        tools.add(iconButton("bank", "Show this loadout in the bank", actions::filter));
        tools.add(iconButton("all", "Show the full bank", actions::unfilter));
        header.add(Box.createVerticalStrut(8));
        header.add(tools);
        aiProgress.setMaximumSize(new Dimension(240, 8));
        aiProgress.setForeground(GREEN);
        aiProgress.setBackground(TILE);
        aiProgress.setVisible(false);
        header.add(Box.createVerticalStrut(4));
        header.add(aiProgress);
        aiState.setForeground(MUTED);
        aiState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        aiState.setVisible(false);
        header.add(aiState);
        add(header, BorderLayout.NORTH);
        content.setOpaque(false);
        JPanel loadout = new JPanel(new BorderLayout());
        loadout.setOpaque(false);
        loadout.add(content, BorderLayout.NORTH);
        JScrollPane gearScroll = new JScrollPane(loadout);
        gearScroll.setBorder(null);
        gearScroll.getViewport().setBackground(BG);
        gearScroll.getVerticalScrollBar().setUnitIncrement(18);
        gearScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        notes.setEditable(false);
        notes.setBackground(BG);
        notes.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        JScrollPane noteScroll = new JScrollPane(notes);
        noteScroll.setBorder(null);
        noteScroll.getVerticalScrollBar().setUnitIncrement(18);
        noteScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        tabs.addPage("loadout", "Loadout", "Gear loadout", gearScroll);
        tabs.addPage("notes", "Notes", "Task notes", noteScroll);
        tabs.addPage("bank", "Bank", "Bank guide", bankGuide);
        add(tabs, BorderLayout.CENTER);
        JPanel footer = new JPanel(new GridLayout(1, 4, 6, 0));
        footer.setOpaque(false);
        footer.add(
                iconButton(
                        "edit",
                        "Edit selected build and Markdown notes",
                        () -> {
                            if (activeSetup != null)
                                SetupEditor.open(gson, this, snapshot, activeSetup, actions::save);
                        }));
        footer.add(
                iconButton(
                        "save", "Save your currently worn gear and inventory", actions::capture));
        footer.add(
                iconButton("export", "Export your bank snapshot as JSON", actions::exportSnapshot));
        footer.add(
                iconButton(
                        "delete",
                        "Delete the selected saved build",
                        () -> {
                            if (activeSetup != null
                                    && JOptionPane.showConfirmDialog(
                                                    this,
                                                    "Delete " + activeSetup.name + "?",
                                                    "Delete build",
                                                    JOptionPane.YES_NO_OPTION)
                                            == JOptionPane.YES_OPTION)
                                actions.delete(activeSetup.id);
                        }));
        add(footer, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(260, 850));
    }

    private JButton iconButton(String icon, String description, Runnable action) {
        JButton b = new JButton(new GuideIcon(icon));
        b.setToolTipText(description);
        b.getAccessibleContext().setAccessibleName(description);
        b.setPreferredSize(new Dimension(32, 32));
        b.setMaximumSize(new Dimension(40, 32));
        b.setBackground(TILE);
        b.setBorder(new LineBorder(new Color(60, 70, 80)));
        b.setFocusable(true);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void showSettings() {
        JComboBox<String> style =
                new JComboBox<>(new String[] {"melee", "ranged", "magic", "hybrid"});
        style.setSelectedItem(taskStyle.getSelectedItem());
        JCheckBox auto =
                new JCheckBox("Auto-select saved build for new tasks", autoTask.isSelected());
        JSpinner port = new JSpinner(new SpinnerNumberModel(apiPort, 1024, 65535, 1));
        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Preferred combat style"));
        form.add(style);
        form.add(auto);
        JPanel aiRow = new JPanel(new BorderLayout());
        aiRow.add(new JLabel("AI provider, model and API key"), BorderLayout.CENTER);
        aiRow.add(
                iconButton(
                        "settings",
                        "Configure AI provider, model and session key",
                        actions::aiSettings),
                BorderLayout.EAST);
        form.add(aiRow);
        JCheckBox colourBank = new JCheckBox("Colour all bank items", actions.bankGuideEnabled());
        form.add(colourBank);
        JCheckBox connection =
                new JCheckBox(
                        "Enable local assistant connection (restart required)",
                        actions.assistantConnectionEnabled());
        form.add(connection);
        form.add(new JLabel("Local API port (restart required after changing)"));
        form.add(port);
        form.add(new JLabel(status.getText()));
        form.add(new JLabel(potions.getText()));
        if (snapshot != null)
            form.add(
                    new JLabel(
                            "<html>Real levels: "
                                    + GearUpgrades.escape(snapshot.levels.toString())
                                    + "</html>"));
        if (JOptionPane.showConfirmDialog(
                        this,
                        form,
                        "Ayra's Gear Guide settings",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION) {
            actions.preferences((String) style.getSelectedItem(), auto.isSelected());
            actions.bankGuideEnabled(colourBank.isSelected());
            actions.assistantConnectionEnabled(connection.isSelected());
            int chosen = (Integer) port.getValue();
            if (chosen != apiPort) actions.port(chosen);
        }
    }

    private void copyRequest() {
        if (snapshot == null || !snapshot.task.known || snapshot.task.remaining <= 0) {
            error("No active task detected yet.");
            return;
        }
        String request =
                "Use the /v1/task hook in Ayra's Gear Guide. Select my existing "
                        + taskStyle.getSelectedItem()
                        + " build for "
                        + snapshot.task.name
                        + " if one exists; otherwise create and push it from my current bank."
                        + " Preserve my edited builds. Choose task-relevant travel and utility"
                        + " items from my owned supplies, including explicit rune supplies in the"
                        + " optional runes array. Format task notes as Markdown with"
                        + " headings, bullets and numbered fight steps.";
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(request), null);
        aiStatus("Build request copied");
    }

    void previewNotes() {
        tabs.select("notes");
    }

    private void syncChoices(GuideStore.Saved saved) {
        if (saved == null || choices.isPopupVisible()) return;
        java.util.List<String> next = new ArrayList<>();
        for (Setup setup : saved.setups.values()) next.add(setup.id + "|" + setup.name);
        updating = true;
        try {
            if (!next.equals(lastChoices)) {
                choices.removeAllItems();
                for (Setup setup : saved.setups.values()) choices.addItem(setup);
                lastChoices = next;
            }
            Setup selected = null;
            for (int i = 0; i < choices.getItemCount(); i++)
                if (Objects.equals(choices.getItemAt(i).id, saved.active))
                    selected = choices.getItemAt(i);
            choices.setSelectedItem(selected);
        } finally {
            updating = false;
        }
    }

    private JPanel column() {
        JPanel p =
                new JPanel() {
                    @Override
                    protected void addImpl(Component component, Object constraints, int index) {
                        if (component instanceof JComponent)
                            ((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
                        super.addImpl(component, constraints, index);
                    }
                };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    public void update(Snapshot s, GuideStore.Saved saved, int port) {
        apiPort = port > 0 ? port : actions.connectionPort();
        snapshot = s;
        latestSaved = saved;
        syncChoices(saved);
        updating = true;
        Setup active = saved.setups.get(saved.active);
        activeSetup = active;
        taskStyle.setSelectedItem(saved.preferredStyle);
        autoTask.setSelected(saved.autoSelect);
        updating = false;
        taskLabel.setText(
                s == null || !s.task.known
                        ? "Task not detected"
                        : s.task.remaining <= 0
                                ? "No active Slayer task"
                                : s.task.name + " · " + s.task.remaining);
        taskLabel.setToolTipText(s == null ? "" : s.task.name + " " + s.task.location);
        status.setText(
                port > 0
                        ? "Local connection · " + port
                        : actions.assistantConnectionEnabled()
                                ? "Assistant connection unavailable; restart or change port"
                                : "Local assistant connection off");
        freshness.setText(
                s == null || !s.bankKnown
                        ? "Open your bank to share items"
                        : s.bankOpen
                                ? "Bank synced · " + s.counts().size() + " items"
                                : "Bank cached · reopen to refresh");
        if (s != null && s.bankKnown && !s.bankOpen) {
            String age = "unknown age";
            try {
                long minutes =
                        Math.max(
                                0,
                                java.time.Duration.between(
                                                java.time.Instant.parse(s.bankCapturedAt),
                                                java.time.Instant.now())
                                        .toMinutes());
                age =
                        minutes < 60
                                ? minutes + "m ago"
                                : minutes < 1440
                                        ? (minutes / 60) + "h ago"
                                        : (minutes / 1440) + "d ago";
            } catch (RuntimeException ignored) {
            }
            freshness.setText("Saved bank · " + age);
            freshness.setToolTipText(
                    "Last observed: "
                            + s.bankCapturedAt
                            + ". Open bank to refresh; quantities may have changed.");
        } else freshness.setToolTipText(null);
        long potionTypes =
                s == null ? 0 : s.potionStorage.stream().filter(p -> p.totalDoses > 0).count();
        potions.setText(
                s != null && s.potionStorageKnown
                        ? "Potion storage · " + potionTypes + " types"
                        : "Open potion storage to sync");
        String markdown =
                active == null
                        ? "## Task notes\nSelect a saved build to see its guide."
                        : active.notes;
        if (!Objects.equals(markdown, lastNotes)) {
            notes.setText(NotesMarkdown.render(markdown));
            notes.setCaretPosition(0);
            lastNotes = markdown;
        }
        freshness.setToolTipText(
                (freshness.getToolTipText() == null ? "" : freshness.getToolTipText() + " ")
                        + potions.getText());
        content.removeAll();
        if (active == null) {
            content.add(
                    text(
                            s != null && s.task.known && s.task.remaining > 0
                                    ? "No selected build. Check task to reuse a saved build, or"
                                          + " copy a build request for chat if this enemy/style is"
                                          + " new."
                                    : "Open your bank, then ask for a setup in chat. Your equipment"
                                          + " and inventory will appear here when a build arrives.",
                            MUTED));
        } else {
            if (s != null && !Objects.equals(s.revision, active.basedOnRevision)) {
                content.add(
                        text(
                                "Your items or levels changed. Ask for an updated recommendation.",
                                AMBER));
                content.add(Box.createVerticalStrut(8));
            }
            content.add(text("Green: ready   Gold: move item\nRed: missing quantity", MUTED));
            if (s != null) {
                for (Pick pick : active.picks())
                    for (OwnedItem item : s.catalog())
                        if (item.id == pick.itemId && item.equipmentSlot != null) {
                            String blocked = GearRequirements.blocked(item.id, s);
                            if (!blocked.isEmpty())
                                content.add(text(item.name + ": " + blocked, RED));
                            break;
                        }
                Map<Integer, Long> needs = new LinkedHashMap<>();
                for (Pick p : active.picks()) needs.merge(p.itemId, p.quantity, Long::sum);
                for (Map.Entry<Integer, Long> n : s.shortages(needs).entrySet())
                    content.add(text("Missing " + n.getValue() + " × " + name(n.getKey()), RED));
                if (s.potionStorageKnown
                        && s.potionStorage.stream()
                                .anyMatch(
                                        p ->
                                                p.variants.values().stream()
                                                        .anyMatch(v -> needs.containsKey(v.id))))
                    content.add(
                            text(
                                    "For stored potions, select the matching withdrawal dose in"
                                            + " potion storage.",
                                    MUTED));
            }
            heading("EQUIPMENT");
            JPanel equipment = new JPanel(new GridLayout(5, 3, 4, 4));
            equipment.setOpaque(false);
            String[] order = {
                null, "HEAD", null, "CAPE", "AMULET", "AMMO", "WEAPON", "BODY", "SHIELD", null,
                "LEGS", null, "GLOVES", "BOOTS", "RING"
            };
            for (String slot : order)
                equipment.add(
                        slot == null
                                ? blank()
                                : tile(active.equipment.get(slot), slot, SLOTS.get(slot), true));
            content.add(equipment);
            heading("INVENTORY");
            Map<Integer, Pick> inv = new HashMap<>();
            for (InventoryPick p : active.inventory) inv.put(p.slot, p.item);
            JPanel inventory = new JPanel(new GridLayout(7, 4, 4, 4));
            inventory.setOpaque(false);
            for (int i = 0; i < 28; i++)
                inventory.add(tile(inv.get(i), "Slot " + (i + 1), i, false));
            content.add(inventory);
            if (!active.runes.isEmpty()) {
                heading("RUNE SUPPLIES");
                content.add(
                        text(
                                "Extra bank supplies.\n"
                                        + "Fill pouch / charge items manually.\n"
                                        + "Pouch contents are not checked.",
                                MUTED));
                JPanel runes = new JPanel(new GridLayout(0, 4, 4, 4));
                runes.setOpaque(false);
                for (Pick rune : active.runes) runes.add(tile(rune, "Rune supply", -1, false));
                content.add(runes);
            }
        }
        revalidate();
        repaint();
    }

    private void heading(String label) {
        content.add(Box.createVerticalStrut(12));
        JLabel l = new JLabel(label);
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        content.add(l);
        content.add(Box.createVerticalStrut(6));
    }

    private JPanel blank() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    private String name(int id) {
        if (snapshot != null) for (OwnedItem i : snapshot.catalog()) if (i.id == id) return i.name;
        return "Item " + id;
    }

    private JComponent tile(Pick pick, String label, int slot, boolean equipment) {
        JPanel p =
                new JPanel(new BorderLayout()) {
                    @Override
                    protected void paintChildren(Graphics graphics) {
                        super.paintChildren(graphics);
                        if (!Boolean.TRUE.equals(getClientProperty("upgradeAvailable"))) return;
                        Graphics2D g = (Graphics2D) graphics.create();
                        g.setRenderingHint(
                                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int x = getWidth() - 16;
                        g.setColor(BG);
                        g.fillRoundRect(x - 2, 3, 14, 16, 4, 4);
                        g.setColor(AMBER);
                        g.setStroke(
                                new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.drawLine(x + 5, 15, x + 5, 6);
                        g.drawLine(x + 1, 10, x + 5, 6);
                        g.drawLine(x + 5, 6, x + 9, 10);
                        g.dispose();
                    }
                };
        p.setBackground(TILE);
        p.setPreferredSize(new Dimension(40, 38));
        if (pick == null) {
            JLabel empty =
                    new JLabel(
                            equipment ? label.substring(0, Math.min(4, label.length())) : "·",
                            SwingConstants.CENTER);
            empty.setFont(new Font("SansSerif", Font.PLAIN, 9));
            empty.setForeground(MUTED);
            p.add(empty);
            p.setToolTipText(label + ": leave empty");
            if (snapshot != null)
                for (OwnedItem actual : equipment ? snapshot.equipment : snapshot.inventory)
                    if (actual.slot == slot) {
                        p.setBorder(new LineBorder(AMBER));
                        p.setToolTipText(label + ": remove " + actual.name);
                        break;
                    }
            return p;
        }
        boolean ready = false;
        if (snapshot != null)
            for (OwnedItem actual : equipment ? snapshot.equipment : snapshot.inventory)
                if (actual.slot == slot
                        && actual.id == pick.itemId
                        && !actual.noted
                        && actual.quantity >= pick.quantity) ready = true;
        long owned = snapshot == null ? 0 : snapshot.counts().getOrDefault(pick.itemId, 0L);
        Color state = ready ? GREEN : owned >= pick.quantity ? AMBER : RED;
        OwnedItem selectedItem = null;
        if (snapshot != null)
            for (OwnedItem i : snapshot.catalog())
                if (i.id == pick.itemId) {
                    selectedItem = i;
                    break;
                }
        if (selectedItem != null
                && selectedItem.equipmentSlot != null
                && !GearRequirements.blocked(pick.itemId, snapshot).isEmpty()) state = RED;
        p.setBorder(new LineBorder(state, 2));
        JLabel icon = new JLabel("", SwingConstants.CENTER);
        if (items != null) items.getImage(pick.itemId, (int) pick.quantity, false).addTo(icon);
        else {
            icon.setText(String.valueOf(pick.itemId));
            icon.setForeground(state);
        }
        p.add(icon, BorderLayout.CENTER);
        if (pick.quantity > 1) {
            JLabel q = new JLabel("×" + pick.quantity, SwingConstants.RIGHT);
            q.setForeground(state);
            q.setFont(new Font("SansSerif", Font.PLAIN, 10));
            p.add(q, BorderLayout.SOUTH);
        }
        p.setToolTipText(
                label
                        + ": "
                        + name(pick.itemId)
                        + " ×"
                        + pick.quantity
                        + " — "
                        + (ready
                                ? "Ready"
                                : owned >= pick.quantity ? "Equip / withdraw / move" : "Missing")
                        + (pick.note.isBlank() ? "" : "; " + pick.note));
        if (equipment || (selectedItem != null && selectedItem.equipmentSlot != null)) {
            String gearSlot = label;
            if (!equipment)
                for (Map.Entry<String, Integer> entry : SLOTS.entrySet())
                    if (entry.getValue().equals(selectedItem.equipmentSlot)) {
                        gearSlot = entry.getKey();
                        break;
                    }
            String tooltip =
                    GearUpgrades.tooltip(
                            activeSetup, snapshot, pick.itemId, gearSlot, p.getToolTipText());
            p.setToolTipText(tooltip);
            icon.setToolTipText(tooltip);
            boolean improved = !GearUpgrades.choices(activeSetup, gearSlot, pick.itemId).isEmpty();
            p.putClientProperty("upgradeAvailable", improved);
            p.getAccessibleContext()
                    .setAccessibleName(
                            name(pick.itemId)
                                    + (improved ? " — upgrade available; hover for details" : ""));
        }
        return p;
    }

    private JTextArea text(String value, Color color) {
        JTextArea t = new JTextArea(value);
        t.setEditable(false);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setOpaque(false);
        t.setMaximumSize(new Dimension(240, 80));
        t.setForeground(color);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));
        t.setAlignmentX(LEFT_ALIGNMENT);
        return t;
    }

    public void error(String message) {
        JOptionPane.showMessageDialog(
                this, message, "Ayra's Gear Guide", JOptionPane.ERROR_MESSAGE);
    }
}
