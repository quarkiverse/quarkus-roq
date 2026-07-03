package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.util.Map;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.RoqRouteHandler;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Template;
import io.quarkus.qute.runtime.TemplateProducer;

public class RoqHybridStartup {

    private static final Logger LOG = Logger.getLogger(RoqHybridStartup.class);

    private final Map<String, Supplier<? extends Page>> pages;
    private final RoqSiteConfig siteConfig;

    public RoqHybridStartup(Map<String, Supplier<? extends Page>> pages,
            RoqSiteConfig siteConfig) {
        this.pages = pages;
        this.siteConfig = siteConfig;
    }

    public void cacheStartupPages() {
        LazyValue<TemplateProducer> templateProducer = new LazyValue<>(
                () -> Arc.container().instance(TemplateProducer.class).get());
        RoqCacheManager cacheManager = Arc.container().instance(RoqCacheManager.class).get();

        for (var entry : pages.entrySet()) {
            Page page = entry.getValue().get();
            RoqHybridConfig.CacheMode cacheMode = cacheManager.getCacheMode(page);
            if (cacheMode != RoqHybridConfig.CacheMode.STARTUP) {
                continue;
            }

            String templateId = page.source().template().generatedQuteTemplateId();
            Template template = templateProducer.get().getInjectableTemplate(templateId);
            if (template == null) {
                LOG.warnf("Skipping startup cache for page [%s]: template not found", page.id());
                continue;
            }

            for (String locale : getLocales(page)) {
                try {
                    String rendered = RoqRouteHandler.renderPage(page, template, locale)
                            .toCompletableFuture().join();
                    if (rendered != null) {
                        String cacheKey = cacheManager.cacheKey(page);
                        cacheManager.putStartup(cacheKey, rendered);
                        LOG.debugf("Cached page at startup: %s (locale: %s)", page.id(), locale);
                    }
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to preload startup cache for page [%s] (locale: %s)", page.id(), locale);
                }
            }
        }
    }

    private java.util.List<String> getLocales(Page page) {
        java.util.List<String> locales = new java.util.ArrayList<>();
        Object pageLocale = page.data("locale");
        if (pageLocale != null) {
            locales.add(pageLocale.toString());
            return locales;
        }
        locales.add(siteConfig.defaultLocale());
        return locales;
    }

}
