package com.gearguide;

import java.awt.*;

import javax.swing.Icon;

/** Vector icons remain crisp across UI scaling and do not depend on emoji fonts. */
final class GuideIcon implements Icon {
    private final String kind;

    GuideIcon(String kind) {
        this.kind = kind;
    }

    public int getIconWidth() {
        return 20;
    }

    public int getIconHeight() {
        return 20;
    }

    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c.isEnabled() ? new Color(200, 220, 222) : new Color(90, 100, 110));
        switch (kind) {
            case "link":
                g.drawRoundRect(2, 8, 10, 7, 5, 5);
                g.drawRoundRect(8, 3, 10, 7, 5, 5);
                g.drawLine(7, 12, 13, 7);
                break;
            case "route":
                g.drawOval(2, 2, 16, 16);
                g.drawPolygon(new int[] {13, 7, 8}, new int[] {4, 10, 13}, 3);
                g.drawLine(8, 13, 13, 4);
                break;
            case "sort":
                for (int i = 0; i < 3; i++) {
                    g.drawLine(7, 4 + i * 6, 17 - i * 3, 4 + i * 6);
                }
                g.drawLine(3, 2, 3, 17);
                g.drawLine(1, 14, 3, 17);
                g.drawLine(3, 17, 5, 14);
                break;
            case "settings":
                g.drawOval(5, 5, 10, 10);
                g.drawOval(8, 8, 4, 4);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    g.drawLine(
                            10 + (int) (7 * Math.cos(a)),
                            10 + (int) (7 * Math.sin(a)),
                            10 + (int) (9 * Math.cos(a)),
                            10 + (int) (9 * Math.sin(a)));
                }
                break;
            case "refresh":
                g.drawArc(3, 3, 14, 14, 35, 290);
                g.drawLine(16, 2, 16, 7);
                g.drawLine(16, 7, 11, 7);
                break;
            case "ai":
                g.drawPolygon(
                        new int[] {10, 12, 18, 12, 10, 8, 2, 8},
                        new int[] {1, 7, 10, 12, 19, 12, 10, 7},
                        8);
                break;
            case "stop":
                g.drawRect(5, 5, 10, 10);
                break;
            case "copy":
                g.drawRoundRect(6, 6, 11, 12, 2, 2);
                g.drawLine(3, 14, 3, 2);
                g.drawLine(3, 2, 13, 2);
                break;
            case "edit":
                g.drawLine(4, 15, 14, 3);
                g.drawLine(7, 17, 17, 5);
                g.drawLine(14, 3, 17, 5);
                g.drawLine(4, 15, 3, 18);
                g.drawLine(3, 18, 7, 17);
                break;
            case "save":
                g.drawRect(3, 2, 14, 16);
                g.drawRect(6, 2, 8, 5);
                g.drawRect(6, 11, 8, 7);
                break;
            case "export":
                g.drawLine(10, 2, 10, 13);
                g.drawLine(6, 9, 10, 13);
                g.drawLine(10, 13, 14, 9);
                g.drawLine(3, 13, 3, 18);
                g.drawLine(3, 18, 17, 18);
                g.drawLine(17, 18, 17, 13);
                break;
            case "delete":
                g.drawLine(3, 5, 17, 5);
                g.drawRect(7, 2, 6, 3);
                g.drawRect(5, 5, 10, 13);
                g.drawLine(8, 8, 8, 15);
                g.drawLine(12, 8, 12, 15);
                break;
            case "bank":
                g.drawPolygon(new int[] {2, 10, 18}, new int[] {6, 1, 6}, 3);
                for (int i = 4; i < 18; i += 6) g.drawLine(i, 8, i, 15);
                g.drawLine(2, 18, 18, 18);
                break;
            case "all":
                for (int a = 3; a < 18; a += 6)
                    for (int b = 3; b < 18; b += 6) g.drawRect(a, b, 3, 3);
                break;
        }
        g.dispose();
    }
}
