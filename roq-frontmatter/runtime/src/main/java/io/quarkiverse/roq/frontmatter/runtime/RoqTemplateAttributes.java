package io.quarkiverse.roq.frontmatter.runtime;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.qute.TemplateInstance;

public record RoqTemplateAttributes(String sourceRootPath,
        String sourcePath,
        String siteUrl,
        String sitePath,
        String pageUrl,
        String pagePath) {

    public static final String TEMPLATE_ID = "templateId";
    public static final String SOURCE_PATH = "sourcePath";
    public static final String SOURCE_ROOT_PATH = "sourceRootPath";
    public static final String PAGE_PATH = "pagePath";
    public static final String PAGE_URL = "pageUrl";
    public static final String SITE_PATH = "sitePath";
    public static final String SITE_URL = "siteUrl";

    public static void setPageData(TemplateInstance instance, Page page, Site site) {
        instance.data("page", page);
        instance.data("site", site);
        instance.setAttribute(SOURCE_PATH, page.source().template().file().absolutePath());
        instance.setAttribute(SOURCE_ROOT_PATH, page.source().template().file().siteDirPath());
        instance.setAttribute(SITE_URL, site.url().absolute());
        instance.setAttribute(SITE_PATH, site.url().relative());
        instance.setAttribute(PAGE_URL, page.url().absolute());
        instance.setAttribute(PAGE_PATH, page.url().relative());
    }

}
