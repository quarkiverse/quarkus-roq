package io.quarkiverse.roq.frontmatter.runtime;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.DefaultBean;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

@Singleton
@DefaultBean
public class DefaultRoqPageRenderer implements RoqPageRenderer {

    @Inject
    Instance<Site> site;

    @Override
    public CompletionStage<String> render(Page page, Template template, String locale) {
        return configureTemplateInstance(page, template, locale).renderAsync();
    }

    private TemplateInstance configureTemplateInstance(Page page, Template template, String locale) {
        Site s = site.get();
        TemplateInstance instance = template.instance();
        instance.data("page", page);
        instance.data("site", s);
        instance.setAttribute(RoqTemplateAttributes.SITE_URL, s.url().absolute());
        instance.setAttribute(RoqTemplateAttributes.SITE_PATH, s.url().relative());
        instance.setAttribute(RoqTemplateAttributes.PAGE_URL, page.url().absolute());
        instance.setAttribute(RoqTemplateAttributes.PAGE_PATH, page.url().relative());
        if (locale != null && !locale.isBlank()) {
            instance.setAttribute(TemplateInstance.LOCALE, locale);
        }
        return instance;
    }
}
