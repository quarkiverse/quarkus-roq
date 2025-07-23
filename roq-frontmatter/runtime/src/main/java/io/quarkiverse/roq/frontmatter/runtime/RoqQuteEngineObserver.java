package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.SOURCE_PATH;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.TEMPLATE_ID;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.qute.EngineBuilder;

@ApplicationScoped
public class RoqQuteEngineObserver {

    private final Map<String, String> templatePathMapping;

    @Inject
    public RoqQuteEngineObserver(Site site) {
        this.templatePathMapping = initTemplatePathMapping(site);
    }

    void configureEngine(@Observes EngineBuilder builder) {
        builder.addParserHook(c -> {
            if (isLayoutSourceTemplate(c.getTemplateId())) {
                // Fixes https://github.com/quarkiverse/quarkus-roq/issues/530
                c.addContentFilter(s -> "");
                return;
            }
        });

        builder.addTemplateInstanceInitializer(templateInstance -> {
            final String templateId = resolveOriginalTemplateId(templateInstance.getTemplate().getId());
            templateInstance.setAttribute(TEMPLATE_ID, templateId);
            if (templatePathMapping.containsKey(templateId)) {
                final String pathString = templatePathMapping.get(templateId);
                templateInstance.setAttribute(SOURCE_PATH, pathString);
            }
        });
    }

    private static Map<String, String> initTemplatePathMapping(Site site) {
        if (site == null) {
            return new HashMap<>();
        }
        // A template can be used in multiple pages
        return site.allPages().stream()
                .collect(Collectors.toMap(p -> p.info().generatedTemplateId(), p -> p.info().sourceFile(),
                        (a, b) -> a));
    }
}
