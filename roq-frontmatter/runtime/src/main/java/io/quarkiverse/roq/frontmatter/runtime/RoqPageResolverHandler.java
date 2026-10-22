package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlashIfNoExt;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeLeadingSlash;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.vertx.http.runtime.RoutingUtils;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class RoqPageResolverHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(RoqPageResolverHandler.class);

    public static final String ROQ_PAGE_KEY = "roq.page";

    private final Map<String, String> pageIdsByPath;
    private final Map<String, Page> extractedPaths;
    private final LazyValue<Site> site;

    /**
     * @param pageIdsByPath the routed page ids by resource path, pages are resolved from the {@link Site} bean
     */
    public RoqPageResolverHandler(Map<String, String> pageIdsByPath) {
        this.pageIdsByPath = pageIdsByPath;
        this.extractedPaths = new ConcurrentHashMap<>();
        this.site = new LazyValue<>(() -> Arc.container().instance(Site.class).get());
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
        final String id = pageIdsByPath.get(link);
        return id == null ? null : site.get().page(id);
    }
}
