package io.quarkiverse.roq.plugin.asciidoctorj.deployment;

import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.TemplateContext;
import io.quarkiverse.roq.plugin.asciidoc.deployment.AsciidocHeaderParser;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConfig;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJSectionHelperFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginAsciidoctorJProcessor {

    private static final Set<String> APPLICABLE_EXTENSIONS = Set.of("adoc", "asciidoc");

    private static final String FEATURE = "roq-plugin-asciidoctorj";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem("asciidoctorj", RoqPluginAsciidoctorJProcessor::isApplicable,
                new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection("{#asciidoc}", "{/asciidoc}"));
    }

    private static boolean isApplicable(TemplateContext c) {
        return APPLICABLE_EXTENSIONS.contains(c.getExtension());
    }

    @BuildStep
    RoqFrontMatterHeaderParserBuildItem header(AsciidoctorJConfig config) {
        return AsciidocHeaderParser.createBuildItem(config.escape(), c -> APPLICABLE_EXTENSIONS.contains(c.getExtension()));
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(AsciidoctorJSectionHelperFactory.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(AsciidoctorJConverter.class));
    }

}
