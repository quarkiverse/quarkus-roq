package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.SOURCE_PATH;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.SOURCE_ROOT_PATH;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.TEMPLATE_ID;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.Sources;
import io.quarkus.qute.EngineBuilder;

@ApplicationScoped
public class RoqQuteEngineObserver {

    private final Map<String, SourceFile> templatePathMapping;

    @Inject
    public RoqQuteEngineObserver(Sources sources) {
        this.templatePathMapping = initTemplatePathMapping(sources);
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
                final SourceFile sourceFile = templatePathMapping.get(templateId);
                templateInstance.setAttribute(SOURCE_PATH, sourceFile.absolutePath());
                templateInstance.setAttribute(SOURCE_ROOT_PATH, sourceFile.siteDirPath());
            }
        });
    }

    private static Map<String, SourceFile> initTemplatePathMapping(Sources sources) {
        if (sources == null) {
            return new HashMap<>();
        }
        // A template can be used in multiple pages
        return sources.list().stream()
                .collect(Collectors.toMap(PageInfo::generatedTemplateId, PageInfo::sourceFile,
                        (a, b) -> a));
    }
}
