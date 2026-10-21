package com.gearguide;

import static org.junit.Assert.*;

import org.junit.Test;

public class NotesMarkdownTest {
    @Test
    public void rendersHeadingsListsAndEmphasis() {
        String s =
                NotesMarkdown.render(
                        "# Fight\n"
                                + "## Prep\n"
                                + "- **Prayer**\n"
                                + "- Food\n\n"
                                + "1. Attack\n"
                                + "2. Dodge\n\n"
                                + "Use `slash` and *eat*.");
        assertTrue(s.contains("<h1>Fight</h1>"));
        assertTrue(s.contains("<h2>Prep</h2>"));
        assertTrue(s.contains("<ul>"));
        assertTrue(s.contains("<ol>"));
        assertTrue(s.contains("<b>Prayer</b>"));
        assertTrue(s.contains("<code>slash</code>"));
        assertTrue(s.contains("<i>eat</i>"));
    }

    @Test
    public void untrustedHtmlCannotLoadImages() {
        String s =
                NotesMarkdown.render(
                        "<img src='https://example.com/track'>\n"
                                + "<script>alert(1)</script>\n"
                                + "![image](https://example.com/a.png)");
        assertFalse(s.contains("<img"));
        assertFalse(s.contains("<script"));
        assertTrue(s.contains("&lt;img"));
    }

    @Test
    public void codeAndUnclosedFenceAreSafe() {
        String s = NotesMarkdown.render("```\n<b>x</b>");
        assertTrue(s.contains("<pre>&lt;b&gt;x&lt;/b&gt;\n</pre>"));
    }

    @Test
    public void emptyNotesAreValid() {
        assertTrue(NotesMarkdown.render(null).endsWith("</body></html>"));
    }
}
