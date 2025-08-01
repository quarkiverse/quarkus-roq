package io.quarkiverse.roq.plugin.asciidoctorj.deployment;

import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJSectionHelperFactory;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginAsciidoctorJProcessor {

    private static final String FEATURE = "roq-plugin-asciidoctorj";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void process(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(AsciidoctorJSectionHelperFactory.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(AsciidoctorJConverter.class));
    }

}
