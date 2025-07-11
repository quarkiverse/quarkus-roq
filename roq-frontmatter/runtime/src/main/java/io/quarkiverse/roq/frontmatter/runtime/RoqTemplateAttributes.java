package io.quarkiverse.roq.frontmatter.runtime;

public record RoqTemplateAttributes(String sourcePath,
        String siteUrl,
        String sitePath,
        String pageUrl,
        String pagePath) {

    public static final String TEMPLATE_ID = "templateId";
    public static final String SOURCE_PATH = "sourcePath";
    public static final String PAGE_PATH = "pagePath";
    public static final String PAGE_URL = "pageUrl";
    public static final String SITE_PATH = "sitePath";
    public static final String SITE_URL = "siteUrl";

}
