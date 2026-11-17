package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.SOURCE_PATH;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.SOURCE_ROOT_PATH;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes.TEMPLATE_ID;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.*;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.Sources;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.qute.EngineBuilder;

@ApplicationScoped
public class RoqQuteEngineObserver {

    private final Map<String, SourceFile> templatePathMapping;

    @Inject
    public RoqQuteEngineObserver(Instance<Sources> sources) {
        // There are no sources when the site has no layouts and no pages (e.g. all files are ignored)
        this.templatePathMapping = sources.isResolvable() ? initTemplatePathMapping(sources.get()) : Collections.emptyMap();
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
            final String templateId = templateInstance.getTemplate().getId();
            templateInstance.setAttribute(TEMPLATE_ID, templateId);
            SourceFile sourceFile = templatePathMapping.get(templateId);
            if (sourceFile == null) {
                int fragmentSep = templateId.lastIndexOf('$');
                if (fragmentSep > 0) {
                    sourceFile = templatePathMapping.get(templateId.substring(0, fragmentSep));
                }
            }
            if (sourceFile != null) {
                templateInstance.setAttribute(SOURCE_PATH, sourceFile.absolutePath());
                templateInstance.setAttribute(SOURCE_ROOT_PATH, sourceFile.siteDirPath());
            }
        });
    }

    private static Map<String, SourceFile> initTemplatePathMapping(Sources sources) {
        // A template can be used in multiple pages
        return sources.list().stream()
                .collect(Collectors.toMap(TemplateSource::generatedQuteTemplateId, TemplateSource::file,
                        (a, b) -> a));
    }
}
