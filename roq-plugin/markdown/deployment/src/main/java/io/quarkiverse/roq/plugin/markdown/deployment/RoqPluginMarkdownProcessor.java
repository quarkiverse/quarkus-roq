package io.quarkiverse.roq.plugin.markdown.deployment;

import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginMarkdownProcessor {
    private static final Set<String> APPLICABLE_EXTENSIONS = Set.of("md", "markdown");
    private static final String FEATURE = "roq-plugin-markdown";
    private static final String MARKUP = "markdown";
    private static final RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection SECTION = new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection(
            "{#markdown}", "{/markdown}");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem(MARKUP, c -> APPLICABLE_EXTENSIONS.contains(c.getExtension()), SECTION);
    }

    /**
     * The Markdown of a Markdown page, its source, for the build steps that need a page as Markdown.
     */
    @BuildStep
    RoqMarkdownConverterBuildItem markdownConverter() {
        return new RoqMarkdownConverterBuildItem(MARKUP, new MarkdownSourceConverter(SECTION));
    }

}
