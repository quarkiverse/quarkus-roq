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

    public static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}
