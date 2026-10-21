package com.gearguide;

import static com.gearguide.Models.*;

import static org.junit.Assert.*;

import org.junit.Test;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public class GuidePanelTest {
    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) matches.add(type.cast(child));
            if (child instanceof Container) matches.addAll(find((Container) child, type));
        }
        return matches;
    }

    private static AbstractButton button(Container root, String label) {
        return find(root, AbstractButton.class).stream()
                .filter(b -> label.equals(b.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    public void everyPageRemainsDirectlyClickableAtSidebarWidth() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<String> changes = new ArrayList<>();
                    GuideNavigation navigation = new GuideNavigation(changes::add);
                    navigation.addPage("loadout", "Loadout", "Gear loadout", new JPanel());
                    navigation.addPage("notes", "Notes", "Task notes", new JPanel());
                    navigation.addPage("bank", "Bank", "Bank guide", new JPanel());
                    navigation.setSize(220, 500);
                    navigation.doLayout();
                    ((Container) navigation.getComponent(0)).doLayout();
                    for (int i = 0; i < 20; i++) {
                        button(navigation, "Bank").doClick(0);
                        assertEquals("bank", navigation.selectedPage());
                        button(navigation, "Notes").doClick(0);
                        assertEquals("notes", navigation.selectedPage());
                        button(navigation, "Loadout").doClick(0);
                    }
                    for (AbstractButton tab : find(navigation, AbstractButton.class)) {
                        assertTrue(tab.isVisible());
                        assertTrue(tab.getWidth() >= 60);
                        assertTrue(tab.getX() + tab.getWidth() <= 220);
                    }
                    assertEquals(61, changes.size());
                });
    }

    @Test
    public void backgroundUpdatesPreservePageAndSavedBuildControls() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    GuidePanel panel =
                            new GuidePanel(
                                    null,
                                    new GuidePanel.Actions() {
                                        public void select(String id) {}

                                        public void delete(String id) {}

                                        public void filter() {}

                                        public void unfilter() {}

                                        public void exportSnapshot() {}

                                        public void capture() {}
                                    });
                    Snapshot snapshot = GuideStoreTest.snapshot();
                    GuideStore.Saved saved = new GuideStore.Saved();
                    Setup setup = GuideStoreTest.setup(snapshot);
                    saved.setups.put(setup.id, setup);
                    saved.active = setup.id;
                    panel.update(snapshot, saved, 0);
                    JComboBox<?> builds = find(panel, JComboBox.class).get(0);
                    AtomicInteger modelChanges = new AtomicInteger();
                    builds.getModel()
                            .addListDataListener(
                                    new ListDataListener() {
                                        public void intervalAdded(ListDataEvent e) {
                                            modelChanges.incrementAndGet();
                                        }

                                        public void intervalRemoved(ListDataEvent e) {
                                            modelChanges.incrementAndGet();
                                        }

                                        public void contentsChanged(ListDataEvent e) {
                                            modelChanges.incrementAndGet();
                                        }
                                    });
                    button(panel, "Bank").doClick(0);
                    for (int i = 0; i < 20; i++) panel.update(snapshot, saved, 0);
                    assertTrue(button(panel, "Bank").isSelected());
                    assertEquals(0, modelChanges.get());
                    button(panel, "Notes").doClick(0);
                    assertTrue(button(panel, "Notes").isSelected());
                });
    }

    @Test
    public void bankRefreshPreservesSelectionAndUnsavedAssignment() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    BankGuidePanel panel =
                            new BankGuidePanel((account, id, tab) -> {}, () -> {}, () -> {});
                    Snapshot snapshot = GuideStoreTest.snapshot();
                    panel.update(snapshot, Collections.emptyMap());
                    JList<?> items = find(panel, JList.class).get(0);
                    items.setSelectedIndex(0);
                    int selected = ((OwnedItem) items.getSelectedValue()).id;
                    JComboBox<?> destination = find(panel, JComboBox.class).get(1);
                    destination.setSelectedIndex(8);
                    Object model = items.getModel();
                    for (int i = 0; i < 20; i++) panel.update(snapshot, Collections.emptyMap());
                    assertSame(model, items.getModel());
                    snapshot.bank.add(GuideStoreTest.item(554, "Fire rune", 100, null, true));
                    panel.update(snapshot, Collections.emptyMap());
                    assertEquals(selected, ((OwnedItem) items.getSelectedValue()).id);
                    assertEquals(8, destination.getSelectedIndex());
                });
    }
}
