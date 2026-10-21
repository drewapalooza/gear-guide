package com.gearguide;

import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import java.awt.*;

final class GuideOverlay extends WidgetItemOverlay {
    private final GearGuidePlugin plugin;

    GuideOverlay(GearGuidePlugin plugin) {
        this.plugin = plugin;
        showOnBank();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item) {
        if (plugin.organizing()) {
            BankOrganizer.Choice choice = plugin.bankChoice(itemId);
            Rectangle b = item.getCanvasBounds();
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                Color c = BankOrganizer.COLORS[choice.tab - 1];
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 35));
                g.fillRect(b.x, b.y, b.width, b.height);
                g.setColor(c);
                g.drawRect(b.x, b.y, b.width - 1, b.height - 1);
                String text = "" + choice.tab + (choice.review ? "?" : "");
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                int w = g.getFontMetrics().stringWidth(text) + 4;
                g.setColor(new Color(15, 18, 22, 235));
                g.fillRect(b.x, b.y + b.height - 14, w, 14);
                g.setColor(c);
                g.drawString(text, b.x + 2, b.y + b.height - 3);
            } finally {
                g.dispose();
            }
            plugin.bankTooltip(b, choice);
            return;
        }
        if (!plugin.isHighlighted(itemId)) return;
        Rectangle bounds = item.getCanvasBounds();
        graphics.setColor(new Color(63, 205, 172, 190));
        graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
    }
}
