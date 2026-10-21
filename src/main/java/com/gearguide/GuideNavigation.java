package com.gearguide;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Fixed navigation stays clickable at RuneLite's narrow sidebar width. */
final class GuideNavigation extends JPanel {
    private final CardLayout layout = new CardLayout();
    private final JPanel pages = new JPanel(layout);
    private final JPanel buttons = new JPanel(new GridLayout(1, 0, 3, 0));
    private final ButtonGroup group = new ButtonGroup();
    private final Map<String, JToggleButton> choices = new LinkedHashMap<>();
    private final Consumer<String> changed;
    private String selected;

    GuideNavigation(Consumer<String> changed) {
        super(new BorderLayout(0, 8));
        this.changed = changed;
        setOpaque(false);
        pages.setOpaque(false);
        buttons.setOpaque(false);
        add(buttons, BorderLayout.NORTH);
        add(pages, BorderLayout.CENTER);
    }

    void addPage(String key, String label, String description, Component page) {
        JToggleButton button = new JToggleButton(label);
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
        button.setMinimumSize(new Dimension(0, 30));
        button.setPreferredSize(new Dimension(72, 30));
        button.setMargin(new java.awt.Insets(4, 2, 4, 2));
        button.setBackground(new Color(37, 43, 50));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(new Color(60, 70, 80)));
        button.addActionListener(event -> select(key));
        choices.put(key, button);
        group.add(button);
        buttons.add(button);
        pages.add(page, key);
        if (selected == null) select(key);
    }

    void select(String key) {
        if (!choices.containsKey(key)) throw new IllegalArgumentException("Unknown page: " + key);
        if (key.equals(selected)) return;
        selected = key;
        choices.get(key).setSelected(true);
        layout.show(pages, key);
        changed.accept(key);
    }

    String selectedPage() {
        return selected;
    }
}
