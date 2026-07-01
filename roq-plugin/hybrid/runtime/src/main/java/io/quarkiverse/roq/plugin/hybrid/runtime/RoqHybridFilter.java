package io.quarkiverse.roq.plugin.hybrid.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.RoqPageResolverHandler;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.utils.FuturePages;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;

public class RoqHybridFilter implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(RoqHybridFilter.class);

    private final RoqCacheManager cacheManager;
    private final RoqSiteConfig siteConfig;

    public RoqHybridFilter(RoqCacheManager cacheManager, RoqSiteConfig siteConfig) {
        this.cacheManager = cacheManager;
        this.siteConfig = siteConfig;
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

        if (cacheManager.getCacheMode(page) == RoqHybridConfig.CacheMode.FALSE) {
            rc.next();
            return;
        }

        String cacheKey = cacheManager.cacheKey(page);
        RoqCacheStore.CacheEntry entry = cacheManager.get(cacheKey);
        if (entry != null) {
            long cachedAt = Utils.secondsFactor(entry.cachedAt());
            rc.response().putHeader(HttpHeaders.LAST_MODIFIED, Utils.formatRFC1123DateTime(cachedAt));
            if (Utils.fresh(rc, cachedAt)) {
                rc.response().setStatusCode(304).end();
                return;
            }
        } else {
            rc.response().putHeader(HttpHeaders.LAST_MODIFIED,
                    Utils.formatRFC1123DateTime(Utils.secondsFactor(System.currentTimeMillis())));
        }

        rc.next();
    }
}
