package io.quarkiverse.roq.plugin.asciidoc.common.deployment;

import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.plugin.asciidoc.common.runtime.AsciidocTemplateExtension;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqPluginAsciidocCommonProcessor {

    private static final Set<String> APPLICABLE_EXTENSIONS = Set.of("adoc", "asciidoc");

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem("asciidoc", c -> APPLICABLE_EXTENSIONS.contains(c.getExtension()),
                new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection(
                        "{#asciidoc attributes=page.asciidocAttributes??}", "{/asciidoc}"));
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(AsciidocTemplateExtension.class));
    }

    @BuildStep
    RoqFrontMatterHeaderParserBuildItem header(AsciidocCommonConfig config) {
        return AsciidocHeaderParser.createBuildItem(config.qute(), c -> APPLICABLE_EXTENSIONS.contains(c.getExtension()));
    }

}
