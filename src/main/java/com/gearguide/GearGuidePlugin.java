package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.*;
import com.google.inject.Provides;

import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.*;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.banktags.*;
import net.runelite.client.plugins.banktags.tabs.*;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;

import javax.inject.Inject;
import javax.swing.*;

@PluginDescriptor(
        name = "Gear Guide",
        description = "Save gear setups and organize your bank, with optional AI assistance",
        tags = {"bank", "gear", "inventory", "setup"})
@PluginDependency(BankTagsPlugin.class)
public class GearGuidePlugin extends Plugin {

    static final String TAG = "gear guide active";

    static final String COLOUR_TAG = "gear guide colour groups";

    private final Map<Integer, OwnedItem> colourViewItems = new LinkedHashMap<>();

    @Inject private Client client;

    @Inject private ClientThread clientThread;

    @Inject private ItemManager items;

    @Inject private ClientToolbar toolbar;

    @Inject private OverlayManager overlays;

    @Inject private TagManager tags;

    @Inject private BankTagsService bankTags;

    @Inject private LayoutManager layouts;

    @Inject private GearGuideConfig config;

    @Inject private ConfigManager configManager;

    @Inject private Gson gson;

    private GuideStore store;

    private LocalApi api;

    private AiBuildController ai;

    private GuidePanel panel;

    private NavigationButton navigation;

    private GuideOverlay overlay;

    private BankTabOverlay tabOverlay;

    private BankOrganizer organizer;

    private final Map<Integer, BankOrganizer.Choice> bankChoices = new HashMap<>();

    private volatile boolean bankGuideActive;

    @Inject private net.runelite.client.ui.overlay.tooltip.TooltipManager tooltips;

    private String accountId, bankCapturedAt;

    private List<OwnedItem> bank = new ArrayList<>();

    private boolean bankKnown, wasBankOpen, running;

    private volatile Set<Integer> highlighted = Collections.emptySet();

    private final Map<Integer, OwnedItem> catalog = new HashMap<>();

    private int ticks;

    private String lastPanelState;

    private boolean potionStorageKnown;

    private String potionStorageCapturedAt;

    private List<PotionStock> potionStorage = new ArrayList<>();

    @Provides
    GearGuideConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(GearGuideConfig.class);
    }

    @Override
    protected void startUp() throws Exception {

        Path root = RuneLite.RUNELITE_DIR.toPath().resolve("gear-guide");

        store = new GuideStore(root, gson);

        organizer = new BankOrganizer(root, gson);

        running = true;

        Runnable createPanel =
                () ->
                        panel =
                                new GuidePanel(
                                        gson,
                                        items,
                                        new GuidePanel.Actions() {

                                            public void select(String id) {
                                                change(() -> store.select(id));
                                            }

                                            public void delete(String id) {
                                                change(() -> store.delete(id));
                                            }

                                            public void filter() {
                                                clientThread.invokeLater(
                                                        () -> {
                                                            applyLayout(false);
                                                            if (bankOpen())
                                                                bankTags.openBankTag(
                                                                        TAG,
                                                                        BankTagsService
                                                                                .OPTION_HIDE_TAG_NAME);
                                                        });
                                            }

                                            public void unfilter() {
                                                clientThread.invokeLater(() -> closeFilter());
                                            }

                                            public void groupBank() {
                                                clientThread.invokeLater(
                                                        GearGuidePlugin.this::groupBankByColour);
                                            }

                                            private GearGuidePlugin thisPlugin() {
                                                return GearGuidePlugin.this;
                                            }

                                            public void exportSnapshot() {
                                                export();
                                            }

                                            public void capture() {
                                                clientThread.invokeLater(() -> captureCurrent());
                                            }

                                            public boolean save(Setup setup) {

                                                try {
                                                    store.put(setup);
                                                    setupChanged();
                                                    return true;
                                                } catch (Exception ex) {
                                                    panelError(ex.getMessage());
                                                    return false;
                                                }
                                            }

                                            public void pollTask() {
                                                clientThread.invokeLater(
                                                        () -> {
                                                            onGameTick(new GameTick());
                                                            change(() -> store.matchTask(true));
                                                        });
                                            }

                                            public void aiSettings() {
                                                if (ai != null) ai.configure();
                                            }

                                            public boolean bankGuideEnabled() {
                                                return config.bankGuide();
                                            }

                                            public void bankGuideEnabled(boolean enabled) {
                                                configManager.setConfiguration(
                                                        "gearguide", "bankGuide", enabled);
                                            }

                                            public void bankGuide(boolean active) {
                                                bankGuideActive = active;
                                                if (active)
                                                    clientThread.invokeLater(() -> closeFilter());
                                            }

                                            public void assignBank(
                                                    String account, int item, int tab) {

                                                clientThread.invokeLater(
                                                        () -> {
                                                            try {
                                                                organizer.assign(
                                                                        account, item, tab);
                                                                bankChoices.clear();
                                                                if (COLOUR_TAG.equals(
                                                                        bankTags.getActiveTag()))
                                                                    showColourLayout();
                                                                lastPanelState = null;
                                                                refreshPanel();
                                                            } catch (Exception ex) {
                                                                panelError(ex.getMessage());
                                                            }
                                                        });
                                            }

                                            public void port(int value) {
                                                configManager.setConfiguration(
                                                        "gearguide", "port", value);
                                            }

                                            public int connectionPort() {
                                                return config.port();
                                            }

                                            public boolean assistantConnectionEnabled() {
                                                return config.assistantConnection();
                                            }

                                            public void assistantConnectionEnabled(
                                                    boolean enabled) {
                                                configManager.setConfiguration(
                                                        "gearguide",
                                                        "assistantConnection",
                                                        enabled);
                                            }

                                            public void requestBuild() {
                                                if (ai != null) ai.request();
                                            }

                                            public void cancelBuild() {
                                                if (ai != null) ai.cancel();
                                            }

                                            public void preferences(String style, boolean auto) {
                                                change(() -> store.preferences(style, auto));
                                            }
                                        });

        if (SwingUtilities.isEventDispatchThread()) createPanel.run();
        else SwingUtilities.invokeAndWait(createPanel);

        ai = new AiBuildController(store, panel, root, gson, this::setupChanged);

        if (config.assistantConnection()) {
            api = new LocalApi(store, gson, root.resolve("connection.json"), this::setupChanged);
            try {
                api.start(config.port());
            } catch (Exception ex) {
                api.close();
                api = null;
                SwingUtilities.invokeLater(
                        () ->
                                panel.aiStatus(
                                        "Local connection unavailable. Gear and bank tools still"
                                                + " work; check the port in settings."));
            }
        }

        tags.registerTag(TAG, id -> highlighted.contains(items.canonicalize(id)));

        tags.registerTag(COLOUR_TAG, id -> colourViewItems.containsKey(canonicalBankItem(id)));

        overlay = new GuideOverlay(this);

        overlays.add(overlay);

        tabOverlay = new BankTabOverlay(client, this);
        overlays.add(tabOverlay);

        BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = icon.createGraphics();
        g.setColor(new Color(63, 205, 172));

        g.fillRoundRect(4, 2, 16, 20, 6, 6);
        g.setColor(new Color(25, 30, 35));

        g.drawLine(8, 12, 11, 16);
        g.drawLine(11, 16, 17, 7);
        g.dispose();

        navigation =
                NavigationButton.builder()
                        .tooltip("Gear Guide")
                        .icon(icon)
                        .priority(6)
                        .panel(panel)
                        .build();

        toolbar.addNavigation(navigation);

        refreshPanel();
    }

    @Override
    protected void shutDown() {

        running = false;

        if (ai != null) {
            AiBuildController closing = ai;
            SwingUtilities.invokeLater(closing::close);
            ai = null;
        }

        if (api != null) api.close();

        closeFilter();
        tags.unregisterTag(TAG);
        tags.unregisterTag(COLOUR_TAG);

        if (overlay != null) overlays.remove(overlay);

        if (tabOverlay != null) overlays.remove(tabOverlay);

        if (navigation != null) toolbar.removeNavigation(navigation);

        reset();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {

        if (e.getGameState() == GameState.LOGIN_SCREEN
                || e.getGameState() == GameState.LOGIN_SCREEN_AUTHENTICATOR
                || e.getGameState() == GameState.LOGGING_IN) {

            reset();
            refreshPanel();
        }
    }

    private void reset() {

        bankChoices.clear();
        colourViewItems.clear();
        accountId = null;
        bankKnown = false;
        bank.clear();
        bankCapturedAt = null;

        wasBankOpen = false;
        highlighted = Collections.emptySet();
        catalog.clear();
        lastPanelState = null;

        potionStorageKnown = false;
        potionStorageCapturedAt = null;
        potionStorage = new ArrayList<>();

        if (store != null) store.clear();

        if (organizer != null)
            try {
                organizer.account(null);
            } catch (Exception ignored) {
            }
    }

    @Subscribe
    public void onGameTick(GameTick event) {

        if (!running
                || client.getGameState() != GameState.LOGGED_IN
                || client.getLocalPlayer() == null) return;

        long hash = client.getAccountHash();

        if (hash == 0 || hash == -1) return;

        String nextAccount =
                GuideStore.hash("gear-guide:" + Long.toUnsignedString(hash)).substring(0, 24);

        boolean switched = !Objects.equals(nextAccount, accountId);

        if (switched) {
            closeFilter();
            reset();
            accountId = nextAccount;
        }

        try {
            organizer.account(accountId);
        } catch (Exception ex) {
            panelError(ex.getMessage());
        }

        boolean open = bankOpen();

        Snapshot s = new Snapshot();
        s.accountId = accountId;
        s.bankOpen = open;

        s.task = TaskReader.read(client);

        if (open && client.getItemContainer(InventoryID.BANK) != null) {

            bank = readContainer(InventoryID.BANK);
            bankKnown = true;
            bankCapturedAt = Instant.now().toString();
        }

        s.bankKnown = bankKnown;
        s.bank = new ArrayList<>(bank);
        s.bankCapturedAt = bankCapturedAt;

        Widget potionWidget = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);

        boolean potionTabVisible = potionWidget != null && !potionWidget.isHidden();

        if (open && (potionStorageKnown || potionTabVisible)) {

            try {

                potionStorage = PotionStorageReader.read(client, items);

                potionStorageKnown = true;
                potionStorageCapturedAt = Instant.now().toString();

            } catch (RuntimeException ex) {

                // Do not publish a guessed zero or a partially read dose pool.

                potionStorageKnown = false;
                potionStorage = new ArrayList<>();
                potionStorageCapturedAt = null;
            }
        }

        s.potionStorageKnown = potionStorageKnown;
        s.potionStorage = potionStorage;
        s.potionStorageCapturedAt = potionStorageCapturedAt;

        s.inventory = readContainer(InventoryID.INV);
        s.equipment = readContainer(InventoryID.WORN);

        for (Skill skill : Skill.values())
            if (skill != Skill.OVERALL) s.levels.put(skill.name(), client.getRealSkillLevel(skill));

        s.capturedAt = Instant.now().toString();

        String oldActive = store.active() == null ? null : store.active().id;

        try {
            store.publish(s);
        } catch (Exception ex) {
            panelError("Could not read saved setups: " + ex.getMessage());
            return;
        }

        String newActive = store.active() == null ? null : store.active().id;

        if (switched || !Objects.equals(oldActive, newActive) || (open && !wasBankOpen))
            applyLayout(true);

        if (!open) colourViewItems.clear();

        wasBankOpen = open;

        if (++ticks % 3 == 0 || switched) refreshPanel();
    }

    private boolean bankOpen() {

        Widget w = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        return w != null && !w.isHidden();
    }

    private List<OwnedItem> readContainer(int id) {

        List<OwnedItem> result = new ArrayList<>();

        ItemContainer container = client.getItemContainer(id);

        if (container == null) return result;

        Item[] content = container.getItems();

        for (int slot = 0; slot < content.length; slot++) {

            Item item = content[slot];

            if (item == null || item.getId() < 0 || item.getQuantity() <= 0) continue;

            ItemComposition raw = items.getItemComposition(item.getId());

            if (raw.getPlaceholderTemplateId() != -1) continue;

            int canonical = items.canonicalize(item.getId());

            OwnedItem meta = catalog.get(canonical);

            if (meta == null) {

                ItemComposition def = items.getItemComposition(canonical);

                meta = new OwnedItem();
                meta.id = canonical;
                meta.name = def.getName();
                meta.stackable = def.isStackable();

                ItemStats stats = items.getItemStats(canonical);

                if (stats != null) {

                    meta.stats = gson.toJsonTree(stats).getAsJsonObject();

                    if (stats.getEquipment() != null) {

                        meta.equipmentSlot = stats.getEquipment().getSlot();
                        meta.twoHanded = stats.getEquipment().isTwoHanded();
                    }
                }

                catalog.put(canonical, meta);
            }

            OwnedItem out = new OwnedItem();
            out.id = meta.id;
            out.rawId = item.getId();
            out.name = meta.name;

            out.stackable = meta.stackable;
            out.equipmentSlot = meta.equipmentSlot;
            out.twoHanded = meta.twoHanded;
            out.stats = meta.stats;

            out.noted = raw.getNote() != -1;

            out.slot = slot;
            out.quantity = item.getQuantity();
            result.add(out);
        }

        return result;
    }

    boolean organizing() {
        return config.bankGuide();
    }

    BankOrganizer.Choice bankChoice(int id) {

        BankOrganizer.Choice cached = bankChoices.get(id);
        if (cached != null) return cached;

        int rawId = id;

        ItemComposition def = items.getItemComposition(id);

        if (def.getPlaceholderTemplateId() != -1) id = def.getPlaceholderId();

        id = items.canonicalize(id);

        OwnedItem meta = catalog.get(id);

        if (meta == null) {
            meta = new OwnedItem();
            meta.id = id;
            meta.name = items.getItemComposition(id).getName();

            ItemStats stats = items.getItemStats(id);
            if (stats != null) {
                meta.stats = gson.toJsonTree(stats).getAsJsonObject();
                if (stats.getEquipment() != null)
                    meta.equipmentSlot = stats.getEquipment().getSlot();
            }
        }

        BankOrganizer.Choice choice = BankOrganizer.choose(meta, organizer.overrides());
        bankChoices.put(rawId, choice);
        return choice;
    }

    void bankTooltip(Rectangle bounds, BankOrganizer.Choice choice) {

        net.runelite.api.Point mouse = client.getMouseCanvasPosition();

        if (bounds.contains(mouse.getX(), mouse.getY()))
            tooltips.add(
                    new net.runelite.client.ui.overlay.tooltip.Tooltip(
                            "Tab "
                                    + choice.tab
                                    + " - "
                                    + BankOrganizer.NAMES[choice.tab - 1]
                                    + (choice.review ? " (review)" : "")
                                    + "<br>"
                                    + choice.reason));
    }

    boolean isHighlighted(int id) {
        return highlighted.contains(items.canonicalize(id));
    }

    private void setupChanged() {

        if (!running) return;

        clientThread.invokeLater(
                () -> {
                    if (running) {
                        applyLayout(true);
                        refreshPanel();
                    }
                });
    }

    private void applyLayout(boolean open) {

        Setup setup = store == null ? null : store.active();

        Set<Integer> ids = new HashSet<>();

        if (setup == null) {
            highlighted = ids;
            closeFilter();
            return;
        }

        Layout layout = new Layout(TAG);

        Map<String, Integer> positions =
                Map.ofEntries(
                        Map.entry("HEAD", 1),
                        Map.entry("CAPE", 8),
                        Map.entry("AMULET", 9),
                        Map.entry("AMMO", 11),
                        Map.entry("WEAPON", 16),
                        Map.entry("BODY", 17),
                        Map.entry("SHIELD", 18),
                        Map.entry("LEGS", 25),
                        Map.entry("GLOVES", 32),
                        Map.entry("BOOTS", 33),
                        Map.entry("RING", 34));

        for (Map.Entry<String, Pick> e : setup.equipment.entrySet()) {

            ids.add(e.getValue().itemId);
            layout.setItemAtPos(e.getValue().itemId, positions.get(e.getKey()));
        }

        for (InventoryPick p : setup.inventory) {

            ids.add(p.item.itemId);
            layout.setItemAtPos(p.item.itemId, (p.slot / 4) * 8 + 4 + p.slot % 4);
        }

        highlighted = Collections.unmodifiableSet(ids);
        layouts.saveLayout(layout);

        if (open && organizing()) closeFilter();
        else if (open && bankOpen() && !bankGuideActive)
            bankTags.openBankTag(TAG, BankTagsService.OPTION_HIDE_TAG_NAME);
    }

    private int canonicalBankItem(int id) {
        if (id < 0) return id;

        ItemComposition def = items.getItemComposition(id);

        return items.canonicalize(
                def.getPlaceholderTemplateId() != -1 ? def.getPlaceholderId() : id);
    }

    private void groupBankByColour() {

        if (!bankOpen()) {
            panelError("Open the bank tab you want to group first.");
            return;
        }

        String active = bankTags.getActiveTag();

        if (COLOUR_TAG.equals(active)) {
            showColourLayout();
            return;
        }

        if (active != null) {
            panelError(
                    "Open an ordinary bank tab first, then group by colour. Your existing tag"
                            + " layout is preserved.");
            return;
        }

        Widget parent = client.getWidget(InterfaceID.Bankmain.ITEMS);

        if (parent == null || parent.isHidden()) {
            panelError("Choose an ordinary bank tab; potion storage cannot use colour groups.");
            return;
        }

        Map<Integer, OwnedItem> visible = new LinkedHashMap<>();

        Widget[] children = parent.getDynamicChildren();

        if (children != null)
            for (Widget child : children) {

                if (child == null || child.isHidden() || child.getItemId() < 0) continue;

                int id = canonicalBankItem(child.getItemId());

                OwnedItem meta = catalog.get(id);

                if (meta == null) {
                    meta = new OwnedItem();
                    meta.id = id;
                    meta.name = items.getItemComposition(id).getName();

                    ItemStats stats = items.getItemStats(id);
                    if (stats != null) {
                        meta.stats = gson.toJsonTree(stats).getAsJsonObject();
                        if (stats.getEquipment() != null)
                            meta.equipmentSlot = stats.getEquipment().getSlot();
                    }
                }

                visible.put(id, meta);
            }

        if (visible.isEmpty()) {
            panelError("No items to group in the selected bank view.");
            return;
        }

        colourViewItems.clear();
        colourViewItems.putAll(visible);
        showColourLayout();
    }

    private void showColourLayout() {

        if (!bankOpen() || colourViewItems.isEmpty()) return;

        layouts.saveLayout(
                BankColourLayout.build(
                        COLOUR_TAG, colourViewItems.values(), organizer.overrides()));

        // No ALLOW_MODIFICATIONS: built-in Bank Tags disables item drags in this visual view.

        bankTags.openBankTag(COLOUR_TAG, 0);

        SwingUtilities.invokeLater(
                () -> {
                    if (running) panel.aiStatus("Colour groups open - use Full bank to exit");
                });
    }

    private void closeFilter() {

        if (bankTags != null
                && (TAG.equals(bankTags.getActiveTag())
                        || COLOUR_TAG.equals(bankTags.getActiveTag()))) bankTags.closeBankTag();
    }

    private interface Operation {
        void run() throws Exception;
    }

    private void change(Operation action) {

        try {
            action.run();
            setupChanged();
        } catch (Exception e) {
            panelError(e.getMessage());
        }
    }

    private void refreshPanel() {

        if (panel == null || store == null) return;

        Snapshot s = store.snapshot();
        GuideStore.Saved saved = store.saved();

        String panelState =
                gson.toJson(saved)
                        + (s == null
                                ? "logged-out"
                                : s.revision
                                        + gson.toJson(s.task)
                                        + s.bankOpen
                                        + s.bankKnown
                                        + s.potionStorageKnown
                                        + gson.toJson(s.potionStorage)
                                        + gson.toJson(s.inventory)
                                        + gson.toJson(s.equipment));

        if (panelState.equals(lastPanelState)) return;

        lastPanelState = panelState;

        Map<Integer, Integer> assignments = organizer.overrides();

        SwingUtilities.invokeLater(
                () -> {
                    if (running) {
                        panel.update(s, saved, api == null ? 0 : api.port());
                        panel.bankGuideUpdate(s, assignments);
                    }
                });
    }

    private void panelError(String message) {
        if (panel != null) SwingUtilities.invokeLater(() -> panel.error(message));
    }

    private void export() {

        Snapshot s = store.snapshot();

        if (s == null || !s.bankKnown) {
            panelError("Open your bank first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("bank-snapshot.json"));

        if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {

            Path path = chooser.getSelectedFile().toPath();

            if (Files.exists(path)
                    && JOptionPane.showConfirmDialog(
                                    panel,
                                    "Replace this file?",
                                    "Export bank",
                                    JOptionPane.YES_NO_OPTION)
                            != JOptionPane.YES_OPTION) return;

            try {
                GuideStore.atomicWrite(path, gson.toJson(s));
            } catch (Exception ex) {
                panel.error("Export failed: " + ex.getMessage());
            }
        }
    }

    private void captureCurrent() {

        Snapshot s = store.snapshot();

        if (s == null || !s.bankKnown) {
            panelError("Open your bank first.");
            return;
        }

        SwingUtilities.invokeLater(
                () -> {
                    Setup active = store.active();

                    Setup setup =
                            active == null
                                    ? new Setup()
                                    : gson.fromJson(gson.toJson(active), Setup.class);

                    if (active == null) {

                        setup.enemyType = s.task.enemyType;
                        setup.combatStyle = store.saved().preferredStyle;

                        setup.id =
                                setup.enemyType.isBlank()
                                        ? "saved-" + UUID.randomUUID()
                                        : setup.enemyType + "-" + setup.combatStyle;

                        setup.name =
                                s.task.name.isBlank()
                                        ? "My setup"
                                        : s.task.name + " - " + setup.combatStyle;
                    }

                    setup.accountId = s.accountId;
                    setup.basedOnRevision = s.revision;

                    setup.equipment.clear();
                    setup.inventory.clear();

                    for (OwnedItem i : s.equipment)
                        for (Map.Entry<String, Integer> slot : SLOTS.entrySet())
                            if (slot.getValue() == i.slot)
                                setup.equipment.put(slot.getKey(), new Pick(i.id, i.quantity));

                    for (OwnedItem i : s.inventory) {

                        if (i.noted) {
                            panelError("Unnote inventory items before saving a setup.");
                            return;
                        }

                        InventoryPick p = new InventoryPick();
                        p.slot = i.slot;
                        p.item = new Pick(i.id, i.quantity);
                        setup.inventory.add(p);
                    }

                    SetupEditor.open(
                            gson,
                            panel,
                            s,
                            setup,
                            draft -> {
                                try {
                                    store.put(draft);
                                    setupChanged();
                                    return true;
                                } catch (Exception ex) {
                                    panelError(ex.getMessage());
                                    return false;
                                }
                            });
                });
    }
}
