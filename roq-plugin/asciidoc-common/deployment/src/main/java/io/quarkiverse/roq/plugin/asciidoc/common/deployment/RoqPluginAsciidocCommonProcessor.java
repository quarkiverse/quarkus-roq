package io.quarkiverse.roq.plugin.asciidoc.common.deployment;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem;
import io.quarkiverse.roq.plugin.asciidoc.common.runtime.AsciidocTemplateExtension;
import io.quarkiverse.roq.plugin.asciidoc.common.runtime.RoqAsciidocKeys;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqPluginAsciidocCommonProcessor {

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem("asciidoc",
                c -> RoqAsciidocKeys.ASCIIDOC_EXTENSIONS.contains(c.getExtension()),
                new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection(
                        "{#asciidoc attributes=page.asciidocAttributes??}", "{/asciidoc}"));
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(AsciidocTemplateExtension.class));
    }

    /**
     * The AsciiDoc to Markdown converter, for the build steps that need a page as Markdown; it reads the site's
     * attributes and safe mode as the AsciidoctorJ plugin does for the page.
     */
    @BuildStep
    RoqMarkdownConverterBuildItem markdownConverter(AsciidocCommonConfig config) {
        final var attributes = AsciidocMarkdownConverter.siteAttributes(config.attributes());
        attributes.putIfAbsent("sitegen", "roq");
        return new RoqMarkdownConverterBuildItem("asciidoc",
                new AsciidocMarkdownConverter(attributes, AsciidocMarkdownConverter.includes(config.safeMode())));
    }

    @BuildStep
    RoqFrontMatterHeaderParserBuildItem header(AsciidocCommonConfig config) {
        return AsciidocHeaderParser.createBuildItem(config.qute(),
                c -> RoqAsciidocKeys.ASCIIDOC_EXTENSIONS.contains(c.getExtension()));
    }

}
