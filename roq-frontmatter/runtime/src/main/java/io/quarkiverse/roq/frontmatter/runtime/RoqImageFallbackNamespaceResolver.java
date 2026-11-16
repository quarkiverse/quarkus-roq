package io.quarkiverse.roq.frontmatter.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Arc;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.Expression;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.RawString;
import io.quarkus.qute.Results;

/**
 * Lowest-priority resolver of the {@code img} namespace, so that templates (the SEO tags, themes) can use
 * {@code img:url}, {@code img:path} and {@code img:tag} whether or not the image plugin is installed:
 * <ul>
 * <li>{@code img:url(src, preset)}: the image as a {@link RoqUrl} (the source itself when it already is one, else
 * resolved against the site url), so {@code .absolute} and friends work; the preset is ignored here.</li>
 * <li>{@code img:path(src, preset)}: the source as a path string.</li>
 * <li>{@code img:tag(src, preset)}: a plain {@code <img>}.</li>
 * </ul>
 * Qute asks the resolvers of a namespace from the highest priority down and moves on when one answers
 * {@link Results.NotFound}: qute-web's resolver and the image plugin's (both above Qute's default priority) answer
 * with the generated variants, and this one, at the default priority, answers whatever is left.
 */
@EngineConfiguration
public class RoqImageFallbackNamespaceResolver implements NamespaceResolver {

    @Inject
    Engine engine;

    @Override
    public String getNamespace() {
        return "img";
    }

    @Override
    public CompletionStage<Object> resolve(EvalContext context) {
        String method = context.getName();
        List<Expression> params = context.getParams();
        if (params.isEmpty()) {
            return CompletedStage.of(Results.NotFound.EMPTY);
        }
        return context.evaluate(params.get(0)).thenCompose(srcObj -> {
            if (srcObj == null || srcObj.toString().isBlank()) {
                return CompletedStage.of(Results.NotFound.EMPTY);
            }
            return switch (method) {
                case "url" -> CompletedStage.of((Object) toUrl(srcObj));
                case "path" -> CompletedStage.of((Object) srcObj.toString());
                case "tag" -> renderTag(srcObj);
                default -> CompletedStage.of(Results.NotFound.EMPTY);
            };
        });
    }

    /** The same plain {@code <img>} as the {@code {#img}} user tag. */
    private CompletionStage<Object> renderTag(Object src) {
        return engine.getTemplate("tags/img").instance().data("src", src).data("loading", "lazy").renderAsync()
                .thenApply(RawString::new);
    }

    private static RoqUrl toUrl(Object src) {
        if (src instanceof RoqUrl url) {
            return url;
        }
        String path = src.toString();
        if (RoqUrl.isFullPath(path)) {
            return RoqUrl.fromRoot(null, path);
        }
        Site site = Arc.container().instance(Site.class).get();
        return site.url().root().resolve(path);
    }
}
