package com.gearguide;

import static com.gearguide.Models.*;

import com.google.gson.Gson;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;

/** Human-facing editor; all changes stay in a draft until Save succeeds. */
final class SetupEditor {
    static void open(Component parent, Snapshot snapshot, Setup original, Predicate<Setup> save) {
        if (snapshot == null || !snapshot.bankKnown) {
            JOptionPane.showMessageDialog(parent, "Open your bank first.");
            return;
        }
        Setup draft =
                original == null
                        ? new Setup()
                        : new Gson().fromJson(new Gson().toJson(original), Setup.class);
        if (original == null) {
            draft.enemyType = snapshot.task.known ? snapshot.task.enemyType : "";
            draft.name = snapshot.task.name.isBlank() ? "My setup" : snapshot.task.name;
        }
        draft.accountId = snapshot.accountId;
        draft.basedOnRevision = snapshot.revision;
        JDialog dialog =
                new JDialog(
                        SwingUtilities.getWindowAncestor(parent),
                        "Edit gear setup",
                        Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTextField name = new JTextField(draft.name), enemy = new JTextField(draft.enemyType);
        JComboBox<String> style =
                new JComboBox<>(new String[] {"melee", "ranged", "magic", "hybrid"});
        style.setSelectedItem(draft.combatStyle);
        JPanel fields = new JPanel(new GridLayout(3, 2, 6, 6));
        fields.add(new JLabel("Build name"));
        fields.add(name);
        fields.add(new JLabel("Enemy type (task name)"));
        fields.add(enemy);
        fields.add(new JLabel("Combat style"));
        fields.add(style);
        root.add(fields, BorderLayout.NORTH);
        Map<Integer, OwnedItem> catalog = new TreeMap<>();
        for (OwnedItem i : snapshot.catalog()) catalog.put(i.id, i);
        List<String> slots = new ArrayList<>(SLOTS.keySet());
        Pick[] picks = new Pick[39];
        for (int i = 0; i < 11; i++) picks[i] = draft.equipment.get(slots.get(i));
        for (InventoryPick p : draft.inventory) picks[11 + p.slot] = p.item;
        DefaultTableModel model =
                new DefaultTableModel(new Object[] {"Slot", "Item", "Quantity", "Note"}, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return column == 2 || column == 3;
                    }
                };
        Runnable redraw =
                () -> {
                    model.setRowCount(0);
                    for (int i = 0; i < 39; i++) {
                        Pick p = picks[i];
                        OwnedItem item = p == null ? null : catalog.get(p.itemId);
                        model.addRow(
                                new Object[] {
                                    i < 11 ? slots.get(i) : "Inventory " + (i - 10),
                                    p == null
                                            ? "(empty)"
                                            : item == null ? "Item " + p.itemId : item.name,
                                    p == null ? 1 : p.quantity,
                                    p == null ? "" : p.note
                                });
                    }
                };
        redraw.run();
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        JTextArea notes = new JTextArea(draft.notes, 4, 40);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        JPanel lower = new JPanel(new BorderLayout(5, 5));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton choose = new JButton("Choose item…"),
                clear = new JButton("Clear slot"),
                worn = new JButton("Use current worn gear + inventory");
        tools.add(choose);
        tools.add(clear);
        tools.add(worn);
        lower.add(tools, BorderLayout.NORTH);
        lower.add(new JScrollPane(notes), BorderLayout.CENTER);
        center.add(lower, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        Runnable commitCells =
                () -> {
                    if (table.isEditing()) table.getCellEditor().stopCellEditing();
                    for (int i = 0; i < 39; i++)
                        if (picks[i] != null) {
                            picks[i].quantity = Long.parseLong(model.getValueAt(i, 2).toString());
                            picks[i].note = Objects.toString(model.getValueAt(i, 3), "");
                        }
                };
        choose.addActionListener(
                e -> {
                    int row = table.getSelectedRow();
                    if (row < 0) return;
                    try {
                        commitCells.run();
                    } catch (RuntimeException ex) {
                        JOptionPane.showMessageDialog(
                                dialog, "Enter whole-number quantities first.");
                        return;
                    }
                    List<OwnedItem> options = new ArrayList<>();
                    for (OwnedItem item : catalog.values())
                        if (snapshot.counts().getOrDefault(item.id, 0L) > 0
                                && (row >= 11
                                        || Objects.equals(
                                                item.equipmentSlot, SLOTS.get(slots.get(row)))))
                            options.add(item);
                    OwnedItem picked = pickItem(dialog, options);
                    if (picked != null) {
                        picks[row] = new Pick(picked.id, 1);
                        redraw.run();
                        table.setRowSelectionInterval(row, row);
                    }
                });
        clear.addActionListener(
                e -> {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        try {
                            commitCells.run();
                        } catch (RuntimeException ex) {
                            JOptionPane.showMessageDialog(
                                    dialog, "Enter whole-number quantities first.");
                            return;
                        }
                        picks[row] = null;
                        redraw.run();
                        table.setRowSelectionInterval(row, row);
                    }
                });
        worn.addActionListener(
                e -> {
                    for (OwnedItem i : snapshot.inventory)
                        if (i.noted) {
                            JOptionPane.showMessageDialog(
                                    dialog, "Unnote inventory items before capturing.");
                            return;
                        }
                    Arrays.fill(picks, null);
                    for (OwnedItem item : snapshot.equipment)
                        for (int i = 0; i < 11; i++)
                            if (SLOTS.get(slots.get(i)) == item.slot)
                                picks[i] = new Pick(item.id, item.quantity);
                    for (OwnedItem item : snapshot.inventory)
                        if (item.slot < 28)
                            picks[11 + item.slot] = new Pick(item.id, item.quantity);
                    redraw.run();
                });
        JButton saveButton = new JButton("Save build"), cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(saveButton);
        root.add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(e -> dialog.dispose());
        saveButton.addActionListener(
                e -> {
                    try {
                        commitCells.run();
                        draft.name = name.getText().trim();
                        draft.enemyType = enemyKey(enemy.getText());
                        draft.combatStyle = (String) style.getSelectedItem();
                        draft.notes = notes.getText();
                        if (original == null
                                || !Objects.equals(original.enemyType, draft.enemyType)
                                || !Objects.equals(original.combatStyle, draft.combatStyle))
                            draft.id =
                                    draft.enemyType.isBlank()
                                            ? "saved-" + UUID.randomUUID()
                                            : draft.enemyType.substring(
                                                            0,
                                                            Math.min(45, draft.enemyType.length()))
                                                    + "-"
                                                    + draft.combatStyle;
                        draft.equipment.clear();
                        draft.inventory.clear();
                        draft.replaceExisting = true;
                        for (int i = 0; i < 39; i++)
                            if (picks[i] != null) {
                                if (i < 11) draft.equipment.put(slots.get(i), picks[i]);
                                else {
                                    InventoryPick p = new InventoryPick();
                                    p.slot = i - 11;
                                    p.item = picks[i];
                                    draft.inventory.add(p);
                                }
                            }
                        if (save.test(draft)) dialog.dispose();
                    } catch (RuntimeException ex) {
                        JOptionPane.showMessageDialog(
                                dialog, "Check the quantities and fields: " + ex.getMessage());
                    }
                });
        dialog.setContentPane(root);
        dialog.setSize(720, 780);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static OwnedItem pickItem(Component parent, List<OwnedItem> items) {
        items.sort(Comparator.comparing(i -> i.name));
        JTextField search = new JTextField();
        DefaultListModel<OwnedItem> model = new DefaultListModel<>();
        JList<OwnedItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(
                new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(
                            JList<?> l, Object value, int index, boolean selected, boolean focus) {
                        OwnedItem item = (OwnedItem) value;
                        return super.getListCellRendererComponent(
                                l,
                                item.name
                                        + " ["
                                        + item.id
                                        + "]"
                                        + (item.levelBlock == null || item.levelBlock.isEmpty()
                                                ? ""
                                                : " — " + item.levelBlock),
                                index,
                                selected,
                                focus);
                    }
                });
        Runnable filter =
                () -> {
                    model.clear();
                    String q = search.getText().toLowerCase(Locale.ROOT);
                    for (OwnedItem i : items)
                        if (i.name.toLowerCase(Locale.ROOT).contains(q)
                                || String.valueOf(i.id).equals(q)) model.addElement(i);
                };
        search.getDocument()
                .addDocumentListener(
                        new DocumentListener() {
                            public void insertUpdate(DocumentEvent e) {
                                filter.run();
                            }

                            public void removeUpdate(DocumentEvent e) {
                                filter.run();
                            }

                            public void changedUpdate(DocumentEvent e) {
                                filter.run();
                            }
                        });
        filter.run();
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(search, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(400, 350));
        return JOptionPane.showConfirmDialog(
                                parent, panel, "Search owned items", JOptionPane.OK_CANCEL_OPTION)
                        == JOptionPane.OK_OPTION
                ? list.getSelectedValue()
                : null;
    }
}
