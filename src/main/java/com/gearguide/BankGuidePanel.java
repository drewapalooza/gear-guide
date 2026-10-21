package com.gearguide;

import static com.gearguide.Models.*;

import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;

final class BankGuidePanel extends JPanel {
    interface Assignment {
        void save(String account, int id, int tab);
    }

    private final Assignment assignment;
    private final JList<OwnedItem> list = new JList<>();
    private final JTextField search = new JTextField();
    private final JComboBox<String> filter = new JComboBox<>();
    private final JComboBox<String> destination = new JComboBox<>();
    private final JLabel detail = new JLabel("Select an item to change its tab."),
            count = new JLabel();
    private Snapshot snapshot;
    private Map<Integer, Integer> overrides = Collections.emptyMap();
    private final Map<Integer, BankOrganizer.Choice> classifications = new HashMap<>();
    private List<OwnedItem> sorted = Collections.emptyList();
    private String lastBankState;
    private boolean rebuilding, pending;
    private JScrollPane itemScroll;

    BankGuidePanel(Assignment assignment, Runnable groupBank, Runnable normalBank) {
        super(new BorderLayout(0, 6));
        this.assignment = assignment;
        Color bg = new Color(25, 29, 34);
        setBackground(bg);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(bg);
        top.add(
                new JLabel(
                        "<html><body style='width:190px'>Match the numbered colours to bank tabs"
                            + " 1–9.<br>Drag items yourself. ∞ is the inbox.<br>? = review;"
                            + " assignments are editable.</html>"));
        filter.addItem("All items");
        filter.addItem("Needs review");
        destination.addItem("Automatic suggestion");
        for (int i = 0; i < 9; i++) {
            String name = (i + 1) + " · " + BankOrganizer.NAMES[i];
            filter.addItem(name);
            destination.addItem(name);
            JLabel legend = new JLabel(name);
            legend.setFont(new Font("SansSerif", Font.PLAIN, 11));
            legend.setToolTipText(name);
            legend.setForeground(BankOrganizer.COLORS[i]);
            top.add(legend);
        }
        for (Component component : top.getComponents()) {
            JComponent c = (JComponent) component;
            c.setAlignmentX(LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(240, c.getPreferredSize().height));
        }
        ((JLabel) top.getComponent(0)).setForeground(Color.LIGHT_GRAY);
        search.setMaximumSize(new Dimension(240, 26));
        filter.setMaximumSize(new Dimension(240, 26));
        count.setForeground(Color.LIGHT_GRAY);
        count.setPreferredSize(new Dimension(230, 20));
        count.setMaximumSize(new Dimension(240, 20));
        search.setToolTipText("Search bank items by name");
        top.add(search);
        top.add(filter);
        top.add(count);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        tools.setOpaque(false);
        tools.setMaximumSize(new Dimension(240, 30));
        JButton group = new JButton(new GuideIcon("sort"));
        group.setToolTipText(
                "Group the current bank tab visually by colour (does not move stored items)");
        group.getAccessibleContext().setAccessibleName("Group current bank tab by colour");
        group.addActionListener(e -> groupBank.run());
        tools.add(group);
        JButton normal = new JButton(new GuideIcon("all"));
        normal.setToolTipText("Return to the normal bank for dragging items");
        normal.getAccessibleContext().setAccessibleName("Return to normal bank");
        normal.addActionListener(e -> normalBank.run());
        tools.add(normal);
        for (JButton button : new JButton[] {group, normal}) {
            button.setBackground(new Color(37, 43, 50));
            button.setBorder(BorderFactory.createLineBorder(new Color(60, 70, 80)));
            button.setPreferredSize(new Dimension(34, 28));
        }
        top.add(tools);
        for (Component component : top.getComponents()) {
            ((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
        }
        add(top, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(
                (view, item, index, selected, focus) -> {
                    BankOrganizer.Choice c = classifications.get(item.id);
                    if (c == null) c = BankOrganizer.choose(item, overrides);
                    JLabel label = new JLabel(c.tab + (c.review ? "?" : "") + "  " + item.name);
                    label.setOpaque(true);
                    label.setBackground(selected ? new Color(65, 70, 80) : new Color(30, 33, 38));
                    label.setForeground(BankOrganizer.COLORS[c.tab - 1]);
                    label.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
                    label.setToolTipText(c.reason);
                    return label;
                });
        list.setBackground(bg);
        itemScroll = new JScrollPane(list);
        itemScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        itemScroll.setPreferredSize(new Dimension(220, 180));
        add(itemScroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(3, 3));
        bottom.setBackground(bg);
        detail.setForeground(Color.LIGHT_GRAY);
        bottom.add(detail, BorderLayout.NORTH);
        bottom.add(destination, BorderLayout.CENTER);
        JButton save = new JButton("✓");
        save.setToolTipText("Save selected item's tab assignment (local only)");
        save.getAccessibleContext().setAccessibleName("Save tab assignment");
        save.addActionListener(
                e -> {
                    OwnedItem i = list.getSelectedValue();
                    if (i != null && snapshot != null)
                        assignment.save(snapshot.accountId, i.id, destination.getSelectedIndex());
                });
        bottom.add(save, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        filter.addActionListener(e -> rebuild());
        search.getDocument()
                .addDocumentListener(
                        new DocumentListener() {
                            public void insertUpdate(DocumentEvent e) {
                                rebuild();
                            }

                            public void removeUpdate(DocumentEvent e) {
                                rebuild();
                            }

                            public void changedUpdate(DocumentEvent e) {
                                rebuild();
                            }
                        });
        list.addListSelectionListener(
                e -> {
                    OwnedItem i = list.getSelectedValue();
                    if (!rebuilding && !e.getValueIsAdjusting() && i != null) {
                        destination.setSelectedIndex(overrides.getOrDefault(i.id, 0));
                        detail.setText("<html>" + GearUpgrades.escape(i.name) + "</html>");
                    }
                });
        javax.swing.event.PopupMenuListener popup =
                new javax.swing.event.PopupMenuListener() {
                    public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}

                    public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                        SwingUtilities.invokeLater(
                                () -> {
                                    if (pending) rebuild();
                                });
                    }

                    public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                        SwingUtilities.invokeLater(
                                () -> {
                                    if (pending) rebuild();
                                });
                    }
                };
        filter.addPopupMenuListener(popup);
        destination.addPopupMenuListener(popup);
    }

    void update(Snapshot s, Map<Integer, Integer> assignments) {
        snapshot = s;
        overrides = new HashMap<>(assignments);
        StringBuilder state =
                new StringBuilder(s == null ? "logged-out" : s.accountId + ":" + s.bankKnown);
        state.append(new TreeMap<>(assignments));
        if (s != null)
            for (OwnedItem item : s.bank)
                state.append('|').append(item.id).append(':').append(item.name);
        if (state.toString().equals(lastBankState)) return;
        lastBankState = state.toString();
        classifications.clear();
        sorted = s == null || !s.bankKnown ? Collections.emptyList() : new ArrayList<>(s.bank);
        for (OwnedItem item : sorted)
            classifications.put(item.id, BankOrganizer.choose(item, overrides));
        sorted.sort(
                Comparator.comparingInt((OwnedItem item) -> classifications.get(item.id).tab)
                        .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(item -> item.id));
        rebuild();
    }

    private void rebuild() {
        if (filter.isPopupVisible() || destination.isPopupVisible()) {
            pending = true;
            return;
        }
        pending = false;
        OwnedItem selected = list.getSelectedValue();
        Point position = itemScroll.getViewport().getViewPosition();
        String query = search.getText().toLowerCase(Locale.ROOT);
        int chosen = filter.getSelectedIndex(), review = 0;
        List<OwnedItem> visible = new ArrayList<>();
        int selectedIndex = -1;
        for (OwnedItem i : sorted) {
            BankOrganizer.Choice c = classifications.get(i.id);
            if (c.review) review++;
            if (!i.name.toLowerCase(Locale.ROOT).contains(query)
                    || (chosen == 1 && !c.review)
                    || (chosen >= 2 && c.tab != chosen - 1)) continue;
            if (selected != null && selected.id == i.id) selectedIndex = visible.size();
            visible.add(i);
        }
        rebuilding = true;
        try {
            list.setListData(visible.toArray(new OwnedItem[0]));
            list.setSelectedIndex(selectedIndex);
        } finally {
            rebuilding = false;
        }
        if (selectedIndex < 0) {
            detail.setText("Select an item to change its tab.");
            destination.setSelectedIndex(0);
        }
        itemScroll.getViewport().setViewPosition(position);
        count.setText(
                snapshot == null || !snapshot.bankKnown
                        ? "Open your bank to classify items."
                        : visible.size() + " shown · " + review + " need review");
    }
}
