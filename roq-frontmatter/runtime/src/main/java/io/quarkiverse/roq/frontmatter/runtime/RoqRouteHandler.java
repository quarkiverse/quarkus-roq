package io.quarkiverse.roq.frontmatter.runtime;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.devmode.RoqErrorPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.runtime.TemplateProducer;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.ext.web.RoutingContext;

public class RoqRouteHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(RoqRouteHandler.class);

    private final List<String> compressMediaTypes;
    private final RoqSiteConfig config;

    private final Event<SecurityIdentity> securityIdentityEvent;
    private final CurrentIdentityAssociation currentIdentity;
    private final CurrentVertxRequest currentVertxRequest;
    private final ManagedContext requestContext;
    private final LazyValue<TemplateProducer> templateProducer;

    public RoqRouteHandler(VertxHttpBuildTimeConfig httpBuildTimeConfig,
            RoqSiteConfig config) {
        this.compressMediaTypes = httpBuildTimeConfig.enableCompression()
                ? httpBuildTimeConfig.compressMediaTypes().orElse(List.of())
                : null;
        this.config = config;
        ArcContainer container = Arc.container();
        this.securityIdentityEvent = container.beanManager().getEvent().select(SecurityIdentity.class);
        this.currentVertxRequest = container.instance(CurrentVertxRequest.class).get();
        this.requestContext = container.requestContext();
        this.currentIdentity = container.instance(CurrentIdentityAssociation.class).get();
        this.templateProducer = new LazyValue<>(
                () -> Arc.container().instance(TemplateProducer.class).get());
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

    public static void sendPage(RoutingContext rc, String content, Page page, List<String> compressMediaTypes) {
        String contentType = MimeMapping.getMimeTypeForExtension(page.source().template().targetExtension());
        if (contentType != null) {
            if (contentType.startsWith("text")) {
                rc.response().putHeader(HttpHeaders.CONTENT_TYPE, contentType + ";charset=" + StandardCharsets.UTF_8);
            } else {
                rc.response().putHeader(HttpHeaders.CONTENT_TYPE, contentType);
            }
        }
        if (contentType != null && compressMediaTypes != null && compressMediaTypes.contains(contentType)) {
            String contentEncoding = rc.response().headers().get(HttpHeaders.CONTENT_ENCODING);
            if (contentEncoding != null && HttpHeaders.IDENTITY.toString().equals(contentEncoding)) {
                rc.response().headers().remove(HttpHeaders.CONTENT_ENCODING);
            }
        }
        rc.response().setStatusCode(200).end(content);
    }

    public static CompletionStage<String> renderPage(Page page, Template template, String locale) {
        Site site = Arc.container().beanInstanceSupplier(Site.class).get().get();
        TemplateInstance instance = template.instance();
        instance.data("page", page);
        instance.data("site", site);
        instance.setAttribute(RoqTemplateAttributes.SITE_URL, site.url().absolute());
        instance.setAttribute(RoqTemplateAttributes.SITE_PATH, site.url().relative());
        instance.setAttribute(RoqTemplateAttributes.PAGE_URL, page.url().absolute());
        instance.setAttribute(RoqTemplateAttributes.PAGE_PATH, page.url().relative());
        if (locale != null && !locale.isBlank()) {
            instance.setAttribute(TemplateInstance.LOCALE, locale);
        }
        return instance.renderAsync();
    }

    private void handlePage(RoutingContext rc) {
        Page page = rc.get(RoqPageResolverHandler.ROQ_PAGE_KEY);
        if (page != null) {
            final String templateId = page.source().template().generatedQuteTemplateId();
            Template template = templateProducer.get().getInjectableTemplate(templateId);

            String locale = getLocale(page, rc, config);
            renderPage(page, template, locale).whenComplete((r, t) -> {
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
                    sendPage(rc, r, page, compressMediaTypes);
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

    public static String getLocale(Page page, RoutingContext rc, RoqSiteConfig config) {
        Object pageLocale = page.data("locale");
        if (pageLocale != null) {
            return pageLocale.toString();
        }
        if (rc != null && !rc.acceptableLanguages().isEmpty()) {
            return rc.acceptableLanguages().getFirst().tag();
        }
        if (config.defaultLocale() != null) {
            return config.defaultLocale();
        }
        return null;
    }

}
