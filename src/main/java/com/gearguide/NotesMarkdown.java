package com.gearguide;

import java.util.regex.*;

/** Small, HTML-escaped Markdown renderer for notes; raw HTML and remote images are never loaded. */
final class NotesMarkdown {
    static String render(String markdown) {
        StringBuilder out =
                new StringBuilder(
                        "<html><head><style>body{font-family:sans-serif;font-size:11px;color:#e5e9ed;background:#191d22;margin:10px;}h1{font-size:17px;color:#3fcdac;}h2{font-size:14px;color:#3fcdac;}h3{font-size:12px;color:#3fcdac;}p{margin-top:5px;margin-bottom:9px;}li{margin-bottom:6px;}ul,ol{margin-left:18px;}pre,code{background:#252b32;color:#eab54f;}blockquote{color:#9ba7b1;margin-left:12px;}</style></head><body>");
        String list = "";
        boolean code = false;
        for (String raw : (markdown == null ? "" : markdown).split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("```")) {
                if (!list.isEmpty()) {
                    out.append("</").append(list).append('>');
                    list = "";
                }
                out.append(code ? "</pre>" : "<pre>");
                code = !code;
                continue;
            }
            if (code) {
                out.append(escape(raw)).append('\n');
                continue;
            }
            String type = line.matches("[-*+] .*") ? "ul" : line.matches("\\d+[.)] .*") ? "ol" : "";
            if (!type.equals(list)) {
                if (!list.isEmpty()) out.append("</").append(list).append('>');
                if (!type.isEmpty()) out.append('<').append(type).append('>');
                list = type;
            }
            if (!type.isEmpty()) {
                out.append("<li>")
                        .append(inline(line.replaceFirst("^([-*+]|\\d+[.)])\\s+", "")))
                        .append("</li>");
                continue;
            }
            if (line.isEmpty()) continue;
            Matcher heading = Pattern.compile("^(#{1,3})\\s+(.+)$").matcher(line);
            if (heading.matches()) {
                int n = heading.group(1).length();
                out.append("<h")
                        .append(n)
                        .append('>')
                        .append(inline(heading.group(2)))
                        .append("</h")
                        .append(n)
                        .append('>');
            } else if (line.startsWith("> "))
                out.append("<blockquote>")
                        .append(inline(line.substring(2)))
                        .append("</blockquote>");
            else if (line.matches("---+")) out.append("<hr>");
            else out.append("<p>").append(inline(line)).append("</p>");
        }
        if (code) out.append("</pre>");
        if (!list.isEmpty()) out.append("</").append(list).append('>');
        return out.append("</body></html>").toString();
    }

    private static String inline(String s) {
        s = escape(s);
        s = s.replaceAll("`([^`]+)`", "<code>$1</code>");
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<i>$1</i>");
        // Render link destinations as readable text, without loading or opening anything.
        return s.replaceAll("\\[([^]]+)\\]\\((https?://[^ )]+)\\)", "<b>$1</b> ($2)");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
