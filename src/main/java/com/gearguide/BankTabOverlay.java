package com.gearguide;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/** Paints existing tab hitboxes; never changes widgets, tab contents or menu actions. */
final class BankTabOverlay extends Overlay {
    private final Client client;
    private final GearGuidePlugin plugin;
    private static final Pattern NUMBER = Pattern.compile("(?i)\\btab\\s*([1-9])\\b");

    BankTabOverlay(Client client, GearGuidePlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    static int explicitTab(String text) {
        Matcher m = NUMBER.matcher(text.replaceAll("<[^>]*>", ""));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static boolean tabAction(String[] actions) {
        if (actions == null) return false;
        for (String action : actions)
            if (action != null && action.toLowerCase(Locale.ROOT).startsWith("view tab"))
                return true;
        return false;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.organizing()) return null;
        Widget container = client.getWidget(InterfaceID.Bankmain.TABS);
        if (container == null || container.isHidden()) return null;
        List<Widget> tabs = new ArrayList<>();
        collect(container, tabs, Collections.newSetFromMap(new IdentityHashMap<>()));
        tabs.sort(Comparator.comparingInt(w -> w.getBounds().x));
        Set<Rectangle> drawn = new HashSet<>();
        int ordinal = 0;
        for (Widget widget : tabs) {
            Rectangle bounds = widget.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0 || !drawn.add(bounds)) continue;
            int number = explicitTab(widget.getName() == null ? "" : widget.getName());
            if (number == 0)
                for (String action : widget.getActions()) {
                    number = explicitTab(action == null ? "" : action);
                    if (number > 0) break;
                }
            ordinal++;
            if (number == 0) number = ordinal;
            if (number > 9) continue;
            paintTab(graphics, bounds, number);
            plugin.bankTooltip(
                    bounds,
                    new BankOrganizer.Choice(
                            number,
                            false,
                            "Suggested destination tab; drag items manually in the normal bank"
                                    + " view"));
        }
        return null;
    }

    private static void collect(Widget widget, List<Widget> found, Set<Widget> seen) {
        if (widget == null || !seen.add(widget) || widget.isHidden()) return;
        if (tabAction(widget.getActions())) found.add(widget);
        Widget[] children = widget.getChildren();
        if (children != null) for (Widget child : children) collect(child, found, seen);
        Widget[] fixed = widget.getStaticChildren();
        if (fixed != null) for (Widget child : fixed) collect(child, found, seen);
        Widget[] nested = widget.getNestedChildren();
        if (nested != null) for (Widget child : nested) collect(child, found, seen);
    }

    static void paintTab(Graphics2D graphics, Rectangle b, int number) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Color c = BankOrganizer.COLORS[number - 1];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
            g.fillRect(b.x, b.y, b.width, b.height);
            g.setStroke(new BasicStroke(2));
            g.setColor(c);
            g.drawRect(b.x + 1, b.y + 1, b.width - 3, b.height - 3);
            g.setColor(new Color(15, 18, 22, 235));
            g.fillRoundRect(b.x + 2, b.y + 2, 15, 15, 3, 3);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(c);
            g.drawString(Integer.toString(number), b.x + 5, b.y + 14);
        } finally {
            g.dispose();
        }
    }
}
