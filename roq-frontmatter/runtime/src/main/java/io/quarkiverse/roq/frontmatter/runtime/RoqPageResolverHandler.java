package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlashIfNoExt;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeLeadingSlash;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.vertx.http.runtime.RoutingUtils;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class RoqPageResolverHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(RoqPageResolverHandler.class);

    public static final String ROQ_PAGE_KEY = "roq.page";

    private final Map<String, Supplier<? extends Page>> pages;
    private final Map<String, Page> extractedPaths;

    public RoqPageResolverHandler(Map<String, Supplier<? extends Page>> pages) {
        this.pages = pages;
        this.extractedPaths = new ConcurrentHashMap<>();
    }

    @Override
    public void handle(RoutingContext rc) {
        String requestPath = RoutingUtils.resolvePath(rc);
        Page page = extractedPaths.computeIfAbsent(requestPath, this::extractTemplatePath);
        if (page != null) {
            LOG.debugf("Resolved page: %s", page.id());
            rc.put(ROQ_PAGE_KEY, page);
        }
        rc.next();
    }

    private Page extractTemplatePath(String path) {
        path = removeLeadingSlash(path);
        final String link = addTrailingSlashIfNoExt(path);
        if (pages.containsKey(link)) {
            return pages.get(link).get();
        }
        return null;
    }
}
