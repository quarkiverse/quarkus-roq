package io.quarkiverse.roq.frontmatter.runtime;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
@Unremovable
public class RoqLlmsTxtTemplateExtension {

    public static boolean llmstxt(Page page) {
        return page.data().getBoolean("llmstxt", true);
    }

    public static String plainContent(Page page) {
        String html = page.content();
        if (html == null || html.isEmpty()) {
            return "";
        }
        // Convert block-level HTML to plain text with paragraph breaks
        String text = html;
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</(p|h[1-6]|div|blockquote|ul|ol|table)\\s*>", "\n\n");
        text = text.replaceAll("(?i)</li\\s*>", "\n");
        // Strip all remaining HTML tags
        text = text.replaceAll("<[^>]+>", "");
        // Decode HTML entities (but not &lt;/&gt; which are intentionally escaped code samples)
        text = text.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ");
        // Normalize whitespace: collapse spaces on each line, collapse blank lines
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll(" *\\n", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    public static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}
