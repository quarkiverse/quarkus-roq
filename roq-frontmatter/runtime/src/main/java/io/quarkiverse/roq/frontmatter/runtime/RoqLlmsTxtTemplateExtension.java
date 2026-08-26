package io.quarkiverse.roq.frontmatter.runtime;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
@Unremovable
public class RoqLlmsTxtTemplateExtension {

    public static boolean llmstxt(Page page) {
        return page.data().getBoolean("llmstxt", true);
    }

    /**
     * Returns whether the collection has at least one document visible in llms.txt.<br>
     * Example: "{#if collection.hasLlmstxtEntries}".
     */
    public static boolean hasLlmstxtEntries(RoqCollection collection) {
        for (DocumentPage doc : collection) {
            if (llmstxt(doc)) {
                return true;
            }
        }
        return false;
    }

    public static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}
