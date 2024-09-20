package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.removeLeadingSlash;
import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.literal.NamedLiteral;

import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.Variant;
import io.quarkus.qute.runtime.TemplateProducer;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;

public class RoqRouteHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(RoqRouteHandler.class);

    private final String rootPath;
    private final RoqSiteConfig siteConfig;
    private final Supplier<RoqCollections> roqCollectionsSupplier;
    private final List<String> compressMediaTypes;
    // request path to template path
    private final Map<String, Supplier<Page>> pages;
    private final Map<String, Page> extractedPaths;

    private final Event<SecurityIdentity> securityIdentityEvent;
    private final CurrentIdentityAssociation currentIdentity;
    private final CurrentVertxRequest currentVertxRequest;
    private final ManagedContext requestContext;
    private final LazyValue<TemplateProducer> templateProducer;
    private final LazyValue<Page> site;

    public RoqRouteHandler(String rootPath, HttpBuildTimeConfig httpBuildTimeConfig,
            Supplier<RoqCollections> roqCollectionsSupplier, Map<String, Supplier<Page>> pages, RoqSiteConfig siteConfig) {
        this.rootPath = rootPath;
        this.siteConfig = siteConfig;
        this.roqCollectionsSupplier = roqCollectionsSupplier;
        this.pages = pages;
        this.compressMediaTypes = httpBuildTimeConfig.enableCompression
                ? httpBuildTimeConfig.compressMediaTypes.orElse(List.of())
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
        this.site = new LazyValue<>(() -> Arc.container().instance(Page.class, NamedLiteral.of("site")).get());
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
                rc.addEndHandler(new Handler<AsyncResult<Void>>() {
                    @Override
                    public void handle(AsyncResult<Void> result) {
                        requestContext.destroy(endState);
                    }
                });
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
        String requestPath = rc.request().path();
        LOG.debugf("Handle page: %s", requestPath);

        // Extract the real template path, e.g. /item.html -> web/item
        Page page = extractedPaths.computeIfAbsent(requestPath, this::extractTemplatePath);
        if (page != null) {
            Template template = templateProducer.get().getInjectableTemplate(page.id());
            TemplateInstance originalInstance = template.instance();
            TemplateInstance instance = originalInstance;

            List<MIMEHeader> acceptableTypes = rc.parsedHeaders().accept();
            // Note that we need to obtain the variants from the original template, even if fragment is used
            Variant selected = trySelectVariant(rc, originalInstance, acceptableTypes);

            if (selected != null) {
                instance.setAttribute(TemplateInstance.SELECTED_VARIANT, selected);
                rc.response().putHeader(HttpHeaders.CONTENT_TYPE, selected.getContentType());

                // Compression support - only compress the response if the content type matches the config value
                if (compressMediaTypes != null
                        && compressMediaTypes.contains(selected.getContentType())) {
                    String contentEncoding = rc.response().headers().get(HttpHeaders.CONTENT_ENCODING);
                    if (contentEncoding != null && HttpHeaders.IDENTITY.toString().equals(contentEncoding)) {
                        rc.response().headers().remove(HttpHeaders.CONTENT_ENCODING);
                    }
                }
            }

            if (selected == null && !acceptableTypes.isEmpty()) {
                // The Accept header is set but we are not able to select the appropriate variant
                LOG.errorf("Appropriate template variant not found %s: %s",
                        acceptableTypes.stream().map(MIMEHeader::rawValue).collect(Collectors.toList()),
                        rc.request().path());
                rc.response().setStatusCode(406).end();
            } else {
                instance.data("page", page);
                instance.data("collections", roqCollectionsSupplier.get());
                instance.data("site", site.get());
                instance.renderAsync().whenComplete((r, t) -> {
                    if (t != null) {
                        Throwable rootCause = rootCause(t);
                        LOG.errorf("Error occured while rendering the template [%s]: %s", page.id(), rootCause.toString());
                        rc.fail(rootCause);
                    } else {
                        rc.response().setStatusCode(200).end(r);
                    }
                });
            }
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

    private Variant trySelectVariant(RoutingContext rc, TemplateInstance instance, List<MIMEHeader> acceptableTypes) {
        Object variantsAttr = instance.getAttribute(TemplateInstance.VARIANTS);
        if (variantsAttr != null) {
            @SuppressWarnings("unchecked")
            List<Variant> variants = (List<Variant>) variantsAttr;
            if (!acceptableTypes.isEmpty()) {
                for (MIMEHeader accept : acceptableTypes) {
                    // https://github.com/vert-x3/vertx-web/issues/2388
                    accept.value();
                    for (Variant variant : variants) {
                        if (new ContentType(variant.getContentType()).matches(accept.component(),
                                accept.subComponent())) {
                            return variant;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Page extractTemplatePath(String path) {
        if (path.length() >= rootPath.length()) {
            path = path.substring(rootPath.length());
            path = removeLeadingSlash(path);

            // Check if we have a matching linked template
            final String link = removeTrailingSlash(path);
            if (pages.containsKey(link)) {
                return pages.get(link).get();
            }
        }
        return null;
    }

    final static class ContentType {

        private static final String WILDCARD = "*";

        final String type;
        final String subtype;

        ContentType(String value) {
            int slash = value.indexOf('/');
            this.type = value.substring(0, slash);
            int semicolon = value.indexOf(';');
            this.subtype = semicolon != -1 ? value.substring(slash + 1, semicolon) : value.substring(slash + 1);
        }

        boolean matches(String otherType, String otherSubtype) {
            return (type.equals(otherType) || type.equals(WILDCARD) || otherType.equals(WILDCARD))
                    && (subtype.equals(otherSubtype) || subtype.equals(WILDCARD) || otherSubtype.equals(WILDCARD));
        }

    }

}
