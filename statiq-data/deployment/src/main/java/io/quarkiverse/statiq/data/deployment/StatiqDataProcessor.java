package io.quarkiverse.statiq.data.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.jboss.logging.Logger;

import io.quarkiverse.statiq.data.deployment.items.StatiqDataJsonBuildItem;
import io.quarkiverse.statiq.data.runtime.StatiqDataRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.vertx.core.json.JsonObject;

class StatiqDataProcessor {

    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(StatiqDataProcessor.class);
    private static final String FEATURE = "statiq-data";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateInjectable(BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<StatiqDataJsonBuildItem> statiqDataJsonBuildItems,
            StatiqDataRecorder recorder) {

        for (StatiqDataJsonBuildItem statiqData : statiqDataJsonBuildItems) {
            LOGGER.info("Creating synthetic bean with identifier " + statiqData.getName());
            beansProducer.produce(SyntheticBeanBuildItem.configure(JsonObject.class)
                    .scope(ApplicationScoped.class)
                    .setRuntimeInit()
                    .addQualifier().annotation(Named.class).addValue("value", statiqData.getName()).done()
                    .runtimeValue(recorder.createStatiqDataJson(statiqData.getJsonObject()))
                    .done());
        }
    }

}
