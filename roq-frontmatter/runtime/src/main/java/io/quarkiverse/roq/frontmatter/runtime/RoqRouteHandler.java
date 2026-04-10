package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlashIfNoExt;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeLeadingSlash;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.enterprise.event.Event;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
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
import io.quarkus.runtime.LocalesBuildTimeConfig;
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
    private final RoqSiteConfig config;
    private final LocalesBuildTimeConfig locales;
    // request path to template path
    private final Map<String, Supplier<? extends Page>> pages;
    private final Map<String, Page> extractedPaths;
    // Cache for rendered page content
    private final Map<String, String> cached;

    private final Event<SecurityIdentity> securityIdentityEvent;
    private final CurrentIdentityAssociation currentIdentity;
    private final CurrentVertxRequest currentVertxRequest;
    private final ManagedContext requestContext;
    private final LazyValue<TemplateProducer> templateProducer;
    private final LazyValue<Site> site;

    public RoqRouteHandler(VertxHttpBuildTimeConfig httpBuildTimeConfig,
            Map<String, Supplier<? extends Page>> pages,
            RoqSiteConfig config,
            LocalesBuildTimeConfig locales) {
        this.pages = pages;
        this.compressMediaTypes = httpBuildTimeConfig.enableCompression()
                ? httpBuildTimeConfig.compressMediaTypes().orElse(List.of())
                : null;
        this.config = config;
        this.locales = locales;
        this.extractedPaths = new ConcurrentHashMap<>();
        this.cached = new ConcurrentHashMap<>();
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

    public void cacheStartupPages(RoqSiteConfig config) {
        for (var entry : pages.entrySet()) {
            Page page = entry.getValue().get();
            if (page.getCachedWith(config) == RoqSiteConfig.RuntimeCacheMode.STARTUP) {
                String templateId = page.source().template().generatedQuteTemplateId();
                Template template = templateProducer.get().getInjectableTemplate(templateId);
                if (template == null) {
                    LOG.warnf("Skipping startup cache for page [%s]: template not found", page.id());
                    continue;
                }
                try {
                    String locale = getLocale(page, null);
                    String rendered = renderPage(page, template, locale);
                    if (rendered != null) {
                        String cacheKey = cacheKey(page, locale);
                        cached.put(cacheKey, rendered);
                        LOG.debugf("Cached page at startup: %s", page.id());
                    }
                } catch (Exception e) {
                    Throwable rootCause = rootCause(e);
                    LOG.errorf(rootCause, "Failed to preload startup cache for page [%s]: %s", page.id(), rootCause.toString());
                }
            }
        }
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

            // Check cache mode for this page
            RoqSiteConfig.RuntimeCacheMode cacheMode = page.getCachedWith(config);
            String locale = getLocale(page, rc);
            String cacheKey = cacheKey(page, locale);

            // Try to get from cache if caching is enabled
            if (cacheMode != RoqSiteConfig.RuntimeCacheMode.FALSE) {
                String cachedContent = cached.get(cacheKey);
                if (cachedContent != null) {
                    LOG.debugf("Serving cached content for page: %s", page.id());
                    rc.response().putHeader("X-Roq-Cache-Mode", cacheMode.value());
                    rc.response().putHeader("X-Roq-Cache-Hit", "true");
                    rc.response().setStatusCode(200).end(cachedContent);
                    return;
                }
            }

            // Render the template
            renderPageAsync(page, template, locale).whenComplete((r, t) -> {
                if (t != null) {
                    Throwable rootCause = rootCause(t);
                    LOG.errorf("Error occurred while rendering the template [%s]: %s", page.id(), rootCause.toString());
                    rc.fail(rootCause);
                } else {
                    // Cache the rendered content for any cacheable mode
                    if (cacheMode != RoqSiteConfig.RuntimeCacheMode.FALSE) {
                        LOG.debugf("Caching rendered content for page: %s", page.id());
                        cached.put(cacheKey, r);
                    }
                    // Add cache mode header for all responses
                    rc.response().putHeader("X-Roq-Cache-Mode", cacheMode.value());
                    rc.response().putHeader("X-Roq-Cache-Hit", "false");
                    rc.response().setStatusCode(200).end(r);
                }
            });
        } else {
            LOG.debugf("Template page not found: %s", rc.request().path());
            rc.next();
        }
    }

    private CompletionStage<String> renderPageAsync(Page page, Template template, String locale) {
        return configureTemplateInstance(page, template, locale).renderAsync();
    }

    private String renderPage(Page page, Template template, String locale) {
        return configureTemplateInstance(page, template, locale).render();
    }

    private TemplateInstance configureTemplateInstance(Page page, Template template, String locale) {
        TemplateInstance instance = template.instance();
        instance.data("page", page);
        instance.data("site", site.get());
        instance.setAttribute(RoqTemplateAttributes.SITE_URL, site.get().url().absolute());
        instance.setAttribute(RoqTemplateAttributes.SITE_PATH, site.get().url().relative());
        instance.setAttribute(RoqTemplateAttributes.PAGE_URL, page.url().absolute());
        instance.setAttribute(RoqTemplateAttributes.PAGE_PATH, page.url().relative());
        if (locale != null && !locale.isBlank()) {
            instance.setAttribute(TemplateInstance.LOCALE, locale);
        }
        return instance;
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

    private String getLocale(Page page, RoutingContext rc) {
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
        return locales.defaultLocale().map(Locale::toLanguageTag).orElse(null);
    }

    private String cacheKey(Page page, String locale) {
        if (locale == null || locale.isBlank()) {
            return page.id();
        }
        return page.id() + "::" + locale;
    }

}
