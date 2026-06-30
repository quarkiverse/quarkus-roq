package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.RoqPageResolverHandler;
import io.quarkiverse.roq.frontmatter.runtime.RoqRouteHandler;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.devmode.RoqErrorPage;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.utils.FuturePages;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Template;
import io.quarkus.qute.runtime.TemplateProducer;
import io.quarkus.runtime.LaunchMode;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;

public class RoqHybridHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(RoqHybridHandler.class);

    private final RoqCacheManager cacheManager;
    private final RoqSiteConfig siteConfig;
    private final List<String> compressMediaTypes;
    private final boolean cachingEnabled;
    private final LazyValue<TemplateProducer> templateProducer;

    public RoqHybridHandler(RoqCacheManager cacheManager, RoqSiteConfig siteConfig,
            List<String> compressMediaTypes, boolean cachingEnabled) {
        this.cacheManager = cacheManager;
        this.siteConfig = siteConfig;
        this.compressMediaTypes = compressMediaTypes;
        this.cachingEnabled = cachingEnabled;
        this.templateProducer = new LazyValue<>(
                () -> Arc.container().instance(TemplateProducer.class).get());
    }

    @Override
    public void handle(RoutingContext rc) {
        Page page = rc.get(RoqPageResolverHandler.ROQ_PAGE_KEY);
        if (page == null) {
            rc.next();
            return;
        }

        ConfiguredCollection collection = page instanceof DocumentPage doc
                ? doc.collection().collection()
                : null;

        if (FuturePages.isFutureDateEnforced(siteConfig, collection, page.date())) {
            LOG.debugf("Page '%s' is scheduled for %s, not yet available", page.id(), page.date());
            rc.response().putHeader("X-Roq-Scheduled", page.date().toString());
            rc.put(RoqPageResolverHandler.ROQ_PAGE_KEY, null);
            rc.next();
            return;
        }

        if (!cachingEnabled || cacheManager.getCacheMode(page) == RoqHybridConfig.CacheMode.FALSE) {
            rc.next();
            return;
        }

        String cacheKey = cacheManager.cacheKey(page);
        RoqCacheStore.CacheEntry entry = cacheManager.get(cacheKey, page);
        if (entry != null) {
            long cachedAt = Utils.secondsFactor(entry.cachedAt());
            rc.response().putHeader(HttpHeaders.LAST_MODIFIED, Utils.formatRFC1123DateTime(cachedAt));
            if (Utils.fresh(rc, cachedAt)) {
                rc.response().setStatusCode(304).end();
            } else {
                RoqRouteHandler.sendPage(rc, entry.content(), page, compressMediaTypes);
            }
            return;
        }

        // Cache miss: render, cache, serve
        String templateId = page.source().template().generatedQuteTemplateId();
        Template template = templateProducer.get().getInjectableTemplate(templateId);
        String locale = RoqRouteHandler.getLocale(page, rc, siteConfig);

        RoqRouteHandler.renderPage(page, template, locale).whenComplete((r, t) -> {
            if (t != null) {
                Throwable rootCause = rootCause(t);
                LOG.errorf("Error occurred while rendering the template [%s]: %s", page.id(), rootCause.toString());
                if (LaunchMode.current().isDevOrTest() && rootCause instanceof RoqException) {
                    try {
                        String html = RoqErrorPage.generatePage(rootCause);
                        rc.response().setStatusCode(500)
                                .putHeader(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8")
                                .end(html);
                    } catch (Exception e) {
                        rc.fail(rootCause);
                    }
                } else {
                    rc.fail(rootCause);
                }
            } else {
                cacheManager.put(cacheKey, r);
                rc.response().putHeader(HttpHeaders.LAST_MODIFIED,
                        Utils.formatRFC1123DateTime(Utils.secondsFactor(System.currentTimeMillis())));
                RoqRouteHandler.sendPage(rc, r, page, compressMediaTypes);
            }
        });
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
