package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlashIfNoExt;
import static io.quarkiverse.roq.util.PathUtils.removeLeadingSlash;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.enterprise.event.Event;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.frontmatter.runtime.utils.Sites;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.runtime.TemplateProducer;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.RoutingUtils;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.ext.web.RoutingContext;

public class RoqRouteHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(RoqRouteHandler.class);

    private final List<String> compressMediaTypes;
    // request path to template path
    private final Map<String, Supplier<? extends Page>> pages;
    private final Map<String, Page> extractedPaths;

    private final Event<SecurityIdentity> securityIdentityEvent;
    private final CurrentIdentityAssociation currentIdentity;
    private final CurrentVertxRequest currentVertxRequest;
    private final ManagedContext requestContext;
    private final LazyValue<TemplateProducer> templateProducer;
    private final LazyValue<Site> site;

    public RoqRouteHandler(String rootPath, VertxHttpBuildTimeConfig httpBuildTimeConfig,
            Map<String, Supplier<? extends Page>> pages) {
        this.pages = pages;
        this.compressMediaTypes = httpBuildTimeConfig.enableCompression()
                ? httpBuildTimeConfig.compressMediaTypes().orElse(List.of())
                : null;
        this.extractedPaths = new ConcurrentHashMap<>();
        ArcContainer container = Arc.container();
        this.securityIdentityEvent = container.beanManager().getEvent().select(SecurityIdentity.class);
        this.currentVertxRequest = container.instance(CurrentVertxRequest.class).get();
        this.requestContext = container.requestContext();
        this.currentIdentity = container.instance(CurrentIdentityAssociation.class).get();
        // TemplateProducer is singleton and we want to initialize lazily
        this.templateProducer = new LazyValue<>(
                () -> Arc.container().instance(TemplateProducer.class).get());
        this.site = new LazyValue<>(Sites::getSite);
    }

    @Override
    public void handle(RoutingContext rc) {
        QuarkusHttpUser user = (QuarkusHttpUser) rc.user();

        if (requestContext.isActive()) {
            processCurrentIdentity(rc, user);
            handlePage(rc);
        } else {
            try {
                // Activate the context
                requestContext.activate();
                currentVertxRequest.setCurrent(rc);
                processCurrentIdentity(rc, user);
                // Terminate the context correctly when the response is disposed or an exception is thrown
                final ContextState endState = requestContext.getState();
                rc.addEndHandler(result -> requestContext.destroy(endState));
                handlePage(rc);
            } finally {
                // Deactivate the context
                requestContext.deactivate();
            }
        }
    }

    private void processCurrentIdentity(RoutingContext rc, QuarkusHttpUser user) {
        if (currentIdentity != null) {
            if (user != null) {
                SecurityIdentity identity = user.getSecurityIdentity();
                currentIdentity.setIdentity(identity);
            } else {
                currentIdentity.setIdentity(QuarkusHttpUser.getSecurityIdentity(rc, null));
            }
        }
        if (user != null) {
            securityIdentityEvent.fire(user.getSecurityIdentity());
        }
    }

    private void handlePage(RoutingContext rc) {
        String requestPath = RoutingUtils.resolvePath(rc);
        LOG.debugf("Handle page: %s", requestPath);

        // Extract the real template path, e.g. /item.html -> web/item
        Page page = extractedPaths.computeIfAbsent(requestPath, this::extractTemplatePath);
        if (page != null) {
            final String templateId = page.source().template().generatedQuteTemplateId();
            Template template = templateProducer.get().getInjectableTemplate(templateId);
            TemplateInstance instance = template.instance();
            String contentType = template.getVariant().isPresent() ? template.getVariant().get().getContentType()
                    : MimeMapping.getMimeTypeForFilename(templateId);
            Charset charset = template.getVariant().isPresent() ? template.getVariant().get().getCharset()
                    : StandardCharsets.UTF_8;
            if (contentType != null) {
                if (contentType.startsWith("text")) {
                    rc.response().putHeader(HttpHeaders.CONTENT_TYPE,
                            contentType + ";charset="
                                    + charset);
                } else {
                    rc.response().putHeader(HttpHeaders.CONTENT_TYPE, contentType);
                }
            }

            // Compression support - only compress the response if the content type matches the config value
            if (contentType != null && compressMediaTypes != null
                    && compressMediaTypes.contains(contentType)) {
                String contentEncoding = rc.response().headers().get(HttpHeaders.CONTENT_ENCODING);
                if (contentEncoding != null && HttpHeaders.IDENTITY.toString().equals(contentEncoding)) {
                    rc.response().headers().remove(HttpHeaders.CONTENT_ENCODING);
                }
            }

            instance.data("page", page);
            instance.data("site", site.get());
            instance.setAttribute(RoqTemplateAttributes.SITE_URL, site.get().url().absolute());
            instance.setAttribute(RoqTemplateAttributes.SITE_PATH, site.get().url().relative());
            instance.setAttribute(RoqTemplateAttributes.PAGE_URL, page.url().absolute());
            instance.setAttribute(RoqTemplateAttributes.PAGE_PATH, page.url().relative());
            instance.renderAsync().whenComplete((r, t) -> {
                if (t != null) {
                    Throwable rootCause = rootCause(t);
                    LOG.errorf("Error occurred while rendering the template [%s]: %s", page.id(), rootCause.toString());
                    rc.fail(rootCause);
                } else {
                    rc.response().setStatusCode(200).end(r);
                }
            });
        } else {
            LOG.debugf("Template page not found: %s", rc.request().path());
            rc.next();
        }
    }

    private Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    private Page extractTemplatePath(String path) {
        path = removeLeadingSlash(path);

        // Check if we have a matching linked template
        final String link = addTrailingSlashIfNoExt(path);
        if (pages.containsKey(link)) {
            return pages.get(link).get();
        }
        return null;
    }

}
