package io.quarkiverse.roq.data.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.items.RoqDataBeanBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.runtime.RoqDataRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RoqDataRuntimeProcessor {

    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqDataRuntimeProcessor.class);
    private static final String FEATURE = "roq-data";
    private static final String ANNOTATION_VALUE = "value";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void generateSyntheticBeans(BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            RoqDataRecorder recorder) {

        for (RoqDataJsonBuildItem roqData : roqDataJsonBuildItems) {
            if (roqData.getData() instanceof JsonObject) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonObject.class)
                        .scope(ApplicationScoped.class)
                        .named(roqData.getName())
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .setRuntimeInit()
                        .done());
            } else if (roqData.getData() instanceof JsonArray) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonArray.class)
                        .scope(ApplicationScoped.class)
                        .named(roqData.getName())
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .setRuntimeInit()
                        .done());
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void generateDataMappings(RoqDataRecorder roqDataRecorder, List<RoqDataBeanBuildItem> dataBeanBuildItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeansProducer) {
        for (RoqDataBeanBuildItem beanBuildItem : dataBeanBuildItems) {
            syntheticBeansProducer.produce(SyntheticBeanBuildItem.configure(beanBuildItem.getBeanClass())
                    .scope(beanBuildItem.isRecord() ? Singleton.class : ApplicationScoped.class)
                    .named(beanBuildItem.getName())
                    .runtimeValue(roqDataRecorder.createRoqDataJson(beanBuildItem.getData()))
                    .setRuntimeInit()
                    .done());
        }
    }

}
