package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.removeExtension;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.qute.EngineBuilder;
import io.quarkus.qute.TemplateInstance;

@Singleton
public class RoqFrontmatterDataQuteEngineObserver {

    private final RoqSiteConfig config;

    @Inject
    public RoqFrontmatterDataQuteEngineObserver(RoqSiteConfig config) {
        this.config = config;
    }

    void observeEngineBuilder(@Observes EngineBuilder builder) {
        builder.addTemplateInstanceInitializer(new TemplateInstance.Initializer() {
            @Override
            public void accept(TemplateInstance templateInstance) {
                final String name = removeExtension(templateInstance.getTemplate().getId());
                try (InstanceHandle<Page> instanceHandle = Arc.container().instance(Page.class,
                        NamedLiteral.of(name))) {
                    templateInstance.data("page", instanceHandle.get());
                }
                try (InstanceHandle<Site> instanceHandle = Arc.container().instance(Site.class)) {
                    templateInstance.data("site", instanceHandle.get());
                }
                try (InstanceHandle<RoqCollections> instanceHandle = Arc.container().instance(RoqCollections.class)) {
                    templateInstance.data("collections", instanceHandle.get());
                }
                templateInstance.data("config", config);
            }
        });
    }
}
