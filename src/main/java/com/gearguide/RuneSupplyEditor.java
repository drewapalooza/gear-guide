package com.gearguide;

import static com.gearguide.Models.*;

import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Explicit rune requirements, independent of the 28 inventory slots. */
final class RuneSupplyEditor {
    static List<Pick> edit(Component parent, Snapshot snapshot, List<Pick> original) {
        Map<Integer, OwnedItem> catalog = new LinkedHashMap<>();
        for (OwnedItem item : snapshot.catalog())
            if (RuneSupplies.isRune(item)) catalog.put(item.id, item);
        List<Integer> ids = new ArrayList<>();
        DefaultTableModel model =
                new DefaultTableModel(new Object[] {"Rune", "Quantity", "Note"}, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return column > 0;
                    }
                };
        for (Pick pick : original) {
            ids.add(pick.itemId);
            OwnedItem item = catalog.get(pick.itemId);
            model.addRow(
                    new Object[] {
                        item == null ? "Item " + pick.itemId : item.name, pick.quantity, pick.note
                    });
        }
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JButton add = new JButton("Add rune…"), remove = new JButton("Remove rune");
        add.addActionListener(
                e -> {
                    if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
                    if (ids.size() >= RuneSupplies.MAX_TYPES) return;
                    List<OwnedItem> options = new ArrayList<>();
                    for (OwnedItem item : catalog.values())
                        if (!ids.contains(item.id)
                                && snapshot.counts().getOrDefault(item.id, 0L) > 0)
                            options.add(item);
                    OwnedItem picked = SetupEditor.pickItem(parent, options);
                    if (picked != null) {
                        ids.add(picked.id);
                        model.addRow(new Object[] {picked.name, 1L, ""});
                    }
                });
        remove.addActionListener(
                e -> {
                    if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        ids.remove(row);
                        model.removeRow(row);
                    }
                });
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(
                new JLabel("Extra bank supplies: fill your pouch or charge items manually."),
                BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(620, 300));
        while (JOptionPane.showConfirmDialog(
                        parent,
                        panel,
                        "Rune supplies",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION) {
            if (table.isEditing() && !table.getCellEditor().stopCellEditing()) continue;
            try {
                List<Pick> result = new ArrayList<>();
                for (int row = 0; row < ids.size(); row++) {
                    long quantity = Long.parseLong(model.getValueAt(row, 1).toString());
                    if (quantity < 1 || quantity > Integer.MAX_VALUE)
                        throw new NumberFormatException();
                    Pick pick = new Pick(ids.get(row), quantity);
                    pick.note = Objects.toString(model.getValueAt(row, 2), "");
                    result.add(pick);
                }
                return result;
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(
                        parent, "Enter positive whole-number rune quantities.");
            }
        }
        return null;
    }
}
