package io.quarkiverse.roq.frontmatter.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.qute.EngineBuilder;

@ApplicationScoped
public class RoqQuteEngineObserver {

    public static final String TEMPLATE_ID = "templateId";
    public static final String TEMPLATE_PATH = "templatePath";
    public static final String TEMPLATE_PARENT_DIR = "templateParentDir";

    private final Map<String, Page> templatePathMapping;

    @Inject
    public RoqQuteEngineObserver(Site site) {
        this.templatePathMapping = initTemplatePathMapping(site);
    }

    void configureEngine(@Observes EngineBuilder builder) {
        builder.addParserHook(c -> {
            if (c.getTemplateId().startsWith("layouts")) {
                // Fixes https://github.com/quarkiverse/quarkus-roq/issues/530
                c.addContentFilter(s -> "");
                return;
            }
        });

        builder.addTemplateInstanceInitializer(templateInstance -> {
            final String id = templateInstance.getTemplate().getId();
            templateInstance.setAttribute(TEMPLATE_ID, id);
            if (templatePathMapping.containsKey(id)) {
                final Page page = templatePathMapping.get(id);
                final Path path = Path.of(page.info().absoluteSourceFilePath());
                if (Files.isReadable(path) && Files.isDirectory(path.getParent())) {
                    templateInstance.setAttribute(TEMPLATE_PATH, path);
                }
            }
        });
    }

    private static Map<String, Page> initTemplatePathMapping(Site site) {
        if (site == null) {
            return new HashMap<>();
        }
        return site.allPages().stream()
                .collect(Collectors.toMap(p -> p.info().generatedTemplateId(), p -> p));
    }
}
