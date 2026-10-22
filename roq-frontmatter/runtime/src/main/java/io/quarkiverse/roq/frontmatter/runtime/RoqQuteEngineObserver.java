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

import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.Sources;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.qute.EngineBuilder;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.Results;
import io.quarkus.qute.WithPriority;

@ApplicationScoped
public class RoqQuteEngineObserver {

    public static final String DATA_NAMESPACE = "data";

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

        // Makes `data:foo` an alias of `cdi:foo` (e.g. for data files).
        // When no named bean matches, the built-in Qute `data:` resolver (root template data) is used.
        builder.addNamespaceResolver(NamespaceResolver.builder(DATA_NAMESPACE)
                .priority(WithPriority.DEFAULT_PRIORITY + 1)
                .resolve(RoqQuteEngineObserver::resolveNamedBean)
                .build());

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

    private static Object resolveNamedBean(EvalContext ctx) {
        InjectableBean<?> bean = Arc.container().namedBean(ctx.getName());
        if (bean == null) {
            return Results.NotFound.from(ctx);
        }
        return Arc.container().instance(bean).get();
    }

    private static Map<String, SourceFile> initTemplatePathMapping(Sources sources) {
        final Map<String, SourceFile> templatePathMapping = new HashMap<>();
        if (sources == null) {
            return templatePathMapping;
        }
        // A template can be used in multiple pages
        templatePathMapping.putAll(sources.list().stream()
                .collect(Collectors.toMap(TemplateSource::generatedQuteTemplateId, TemplateSource::file,
                        (a, b) -> a)));
        return templatePathMapping;
    }
}
