package io.quarkiverse.roq.frontmatter.runtime;

public final class RoqTemplates {

    private RoqTemplates() {
    }

    public static final String ROQ_GENERATED_QUTE_PREFIX = "roq-gen/";
    public static final String ROQ_GENERATED_CONTENT_QUTE_PREFIX = "roq-gen-content/";
    public static final String LAYOUTS_DIR = "layouts";
    public static final String THEME_LAYOUTS_DIR_PREFIX = "theme-";

    public static boolean isLayoutSourceTemplate(String templateId) {
        return templateId.startsWith(LAYOUTS_DIR + "/") || templateId.startsWith(THEME_LAYOUTS_DIR_PREFIX + LAYOUTS_DIR + "/");
    }

    public static String resolveGeneratedContentTemplateId(String templateId) {
        return templateId.replace(ROQ_GENERATED_QUTE_PREFIX, ROQ_GENERATED_CONTENT_QUTE_PREFIX);
    }

    public static String resolveOriginalTemplateId(String templateId) {
        return templateId.replace(ROQ_GENERATED_CONTENT_QUTE_PREFIX, ROQ_GENERATED_QUTE_PREFIX);
    }
}
