package io.quarkiverse.roq.theme.antoroq.deployment;

import io.quarkiverse.roq.theme.antoroq.AntoroqTemplateExtensions;
import io.quarkiverse.roq.theme.antoroq.ThemeMessageBundle;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

class AntoroqProcessor {

    private static final String FEATURE = "roq-theme-antoroq";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        additionalBeanProducer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(ThemeMessageBundle.class, AntoroqTemplateExtensions.class)
                .setUnremovable()
                .build());
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createDevUiCard(CurateOutcomeBuildItem bi) {
        CardPageBuildItem pageBuildItem = new CardPageBuildItem();
        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Antoroq Theme")
                .componentLink("qwc-antoroq.js")
                .icon("font-awesome-solid:paint-brush"));
        return pageBuildItem;
    }

}
