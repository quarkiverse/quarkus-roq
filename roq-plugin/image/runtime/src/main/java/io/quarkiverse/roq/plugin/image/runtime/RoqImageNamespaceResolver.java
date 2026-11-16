package io.quarkiverse.roq.plugin.image.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageNamespaceResolver;
import io.quarkiverse.qute.web.image.runtime.ImageTemplateExtension;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.runtime.model.Images;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.RoqImageFallbackNamespaceResolver;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Arc;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.Expression;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.Results;

/**
 * {@code img:url(src, preset)}: the generated variant of an image as a {@link RoqUrl}, so that templates get the
 * root path and {@code .absolute} for free (the SEO image tag relies on it). {@code img:path} and {@code img:tag}
 * are left to qute-web's resolver, which sits just below this one; the frontmatter fallback answers everything when
 * neither applies (see {@link RoqImageFallbackNamespaceResolver}).
 * <p>
 * A source that was not converted at build time is an error when {@code quarkus.qute.image.strict} is on (the
 * plugin's default), otherwise the source itself is returned as a url.
 */
@EngineConfiguration
public class RoqImageNamespaceResolver implements NamespaceResolver {

    /**
     * Above qute-web's resolver, itself above the frontmatter fallback (Qute's default priority).
     */
    public static final int PRIORITY = ImageNamespaceResolver.PRIORITY + 1;

    @Inject
    Images images;

    @Inject
    ImageConfig config;

    @Override
    public String getNamespace() {
        return "img";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public CompletionStage<Object> resolve(EvalContext context) {
        List<Expression> params = context.getParams();
        if (!"url".equals(context.getName()) || params.isEmpty()) {
            return CompletedStage.of(Results.NotFound.EMPTY);
        }
        return context.evaluate(params.get(0)).thenCompose(srcObj -> {
            CompletionStage<Object> presetStage = params.size() > 1
                    ? context.evaluate(params.get(1))
                    : CompletedStage.of("default");
            return presetStage.thenApply(presetObj -> {
                if (srcObj == null || srcObj.toString().isBlank()) {
                    return Results.NotFound.EMPTY;
                }
                String src = srcObj.toString();
                String preset = presetObj != null ? presetObj.toString() : "default";
                String templateId = ImageUtils.templateId(context.resolutionContext().getTemplate());
                ImageTag tag = images.get(templateId, src, preset);
                if (tag != null) {
                    // the variant's path is root-less, the RoqUrl adds the root path; a passed-through source is
                    // the original, whose url the page already resolved
                    return ImageTemplateExtension.bestVariant(tag)
                            .map(variant -> (Object) root().resolve(variant.outputPath()))
                            .orElseGet(() -> srcObj instanceof RoqUrl url ? url : root().resolve(src));
                }
                if (ImageUtils.isRemote(src)) {
                    // not downloaded (disabled, or a failure already reported by the build): the url as is
                    return RoqUrl.fromRoot(null, src);
                }
                if (config.strict()) {
                    throw new RoqImageException(RoqException.builder("Image not converted")
                            .detail("'%s' with preset '%s' in template '%s' was not converted at build time, and"
                                    + " quarkus.qute.image.strict is on.".formatted(src, preset, templateId))
                            .hint("Use a page or site image expression the build can evaluate, or a path under"
                                    + " public/ or static/."));
                }
                return srcObj instanceof RoqUrl url ? url : root().resolve(src);
            });
        });
    }

    private static RootUrl root() {
        return Arc.container().instance(Site.class).get().url().root();
    }
}
