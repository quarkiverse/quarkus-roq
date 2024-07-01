package io.quarkiverse.roq.data.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RoqDataProcessor {

    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqDataProcessor.class);
    private static final String FEATURE = "roq-data";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateInjectable(BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            io.quarkiverse.roq.data.runtime.RoqDataRecorder recorder) {

        for (RoqDataJsonBuildItem roqData : roqDataJsonBuildItems) {
            LOGGER.info("Creating synthetic bean with identifier " + roqData.getName());
            if (roqData.getData() instanceof JsonObject) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonObject.class)
                        .scope(ApplicationScoped.class)
                        .setRuntimeInit()
                        .addQualifier().annotation(Named.class).addValue("value", roqData.getName()).done()
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .done());
            } else if (roqData.getData() instanceof JsonArray) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonArray.class)
                        .scope(ApplicationScoped.class)
                        .setRuntimeInit()
                        .addQualifier().annotation(Named.class).addValue("value", roqData.getName()).done()
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .done());
            }

        }
    }

}
