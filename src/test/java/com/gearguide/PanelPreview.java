package com.gearguide;

import static com.gearguide.Models.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.*;

/** Renders the actual Swing panel with labelled sample data, without logging into the game. */
public final class PanelPreview {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                        BufferedImage pluginIcon =
                                new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D iconGraphics = pluginIcon.createGraphics();
                        iconGraphics.scale(2, 2);
                        iconGraphics.setColor(new Color(63, 205, 172));
                        iconGraphics.fillRoundRect(4, 2, 16, 20, 6, 6);
                        iconGraphics.setColor(new Color(25, 30, 35));
                        iconGraphics.drawLine(8, 12, 11, 16);
                        iconGraphics.drawLine(11, 16, 17, 7);
                        iconGraphics.dispose();
                        ImageIO.write(pluginIcon, "png", new File("icon.png"));
                        Snapshot s = GuideStoreTest.snapshot();
                        s.bankOpen = true;
                        s.bank.add(GuideStoreTest.item(11864, "Slayer helmet", 1, 0, false));
                        s.bank.add(GuideStoreTest.item(9674, "Proselyte hauberk", 1, 4, false));
                        s.bank.add(GuideStoreTest.item(9676, "Proselyte cuisse", 1, 7, false));
                        s.bank.add(GuideStoreTest.item(2434, "Prayer potion(4)", 4, null, false));
                        s.equipment.add(s.bank.remove(0));
                        s.equipment.get(0).slot = 3;
                        s.revision = GuideStore.fingerprint(s);
                        Setup t = GuideStoreTest.setup(s);
                        t.name = "Bloodvelds · sample";
                        t.equipment.put("HEAD", new Pick(11864, 1));
                        t.equipment.put("BODY", new Pick(9674, 1));
                        t.equipment.put("LEGS", new Pick(9676, 1));
                        t.equipment.put("BOOTS", new Pick(11840, 1));
                        for (int i = 0; i < 4; i++) GuideStoreTest.addInventory(t, i, 2434, 1);
                        for (int i = 4; i < 8; i++) GuideStoreTest.addInventory(t, i, 385, 1);
                        t.notes =
                                "# Sample task guide\n"
                                        + "SAMPLE DATA — not your bank.\n"
                                        + "## Preparation\n"
                                        + "- **Protect from Melee**\n"
                                        + "- Bring food and a teleport.\n"
                                        + "## Fight steps\n"
                                        + "1. Drink your combat potion.\n"
                                        + "2. Turn on protection prayer.\n"
                                        + "3. Watch health and prayer.\n"
                                        + "> Bank whenever supplies run low.\n"
                                        + "Use `Show in bank` to find your gear.";
                        GuideStore.Saved saved = new GuideStore.Saved();
                        saved.active = t.id;
                        saved.setups.put(t.id, t);
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
                        panel.update(s, saved, 17654);
                        panel.setSize(260, 900);
                        layout(panel);
                        BufferedImage out = new BufferedImage(260, 900, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = out.createGraphics();
                        panel.printAll(g);
                        g.dispose();
                        ImageIO.write(out, "png", new File("panel-preview.png"));
                        panel.previewNotes();
                        layout(panel);
                        Graphics2D ng = out.createGraphics();
                        panel.printAll(ng);
                        ng.dispose();
                        ImageIO.write(out, "png", new File("notes-preview.png"));
                        panel.bankGuideUpdate(s, java.util.Collections.emptyMap());
                        panel.previewBank();
                        layout(panel);
                        Graphics2D bg = out.createGraphics();
                        panel.printAll(bg);
                        bg.dispose();
                        ImageIO.write(out, "png", new File("bank-guide-preview.png"));
                        BufferedImage tabPreview =
                                new BufferedImage(460, 105, BufferedImage.TYPE_INT_RGB);
                        Graphics2D tabGraphics = tabPreview.createGraphics();
                        tabGraphics.setColor(new Color(25, 29, 34));
                        tabGraphics.fillRect(0, 0, 460, 105);
                        tabGraphics.setColor(Color.WHITE);
                        tabGraphics.drawString(
                                "PREVIEW - sample bank tabs (not a live game screenshot)", 10, 18);
                        for (int tab = 1; tab <= 9; tab++) {
                            Rectangle bounds = new Rectangle(10 + (tab - 1) * 49, 32, 42, 42);
                            BankTabOverlay.paintTab(tabGraphics, bounds, tab);
                        }
                        tabGraphics.setColor(Color.LIGHT_GRAY);
                        tabGraphics.drawString(
                                "Tab numbers and colours match the item highlights.", 10, 94);
                        tabGraphics.dispose();
                        ImageIO.write(tabPreview, "png", new File("bank-tabs-preview.png"));
                        Setup duke = new Setup();
                        duke.enemyType = "duke-sucellus";
                        s.levels.put("ATTACK", 77);
                        s.levels.put("STRENGTH", 85);
                        s.bank.add(GuideStoreTest.item(29589, "Emberlight", 1, 3, false));
                        JToolTip tip = new JToolTip();
                        tip.setTipText(
                                GearUpgrades.tooltip(
                                        duke, s, 19675, "WEAPON", "SAMPLE DATA - Arclight"));
                        Dimension tipSize = tip.getPreferredSize();
                        tip.setSize(tipSize);
                        layout(tip);
                        BufferedImage tooltipImage =
                                new BufferedImage(
                                        tipSize.width, tipSize.height, BufferedImage.TYPE_INT_RGB);
                        Graphics2D tg = tooltipImage.createGraphics();
                        tip.printAll(tg);
                        tg.dispose();
                        ImageIO.write(tooltipImage, "png", new File("upgrade-preview.png"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents())
            if (child instanceof Container) layout((Container) child);
    }
}
