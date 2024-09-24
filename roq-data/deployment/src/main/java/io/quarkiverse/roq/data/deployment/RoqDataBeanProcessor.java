package io.quarkiverse.roq.data.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import io.quarkiverse.roq.data.deployment.items.RoqDataBeanBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.runtime.RoqDataRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RoqDataBeanProcessor {

    private static final String FEATURE = "roq-data";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void generateSyntheticBeans(BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            List<RoqDataBeanBuildItem> dataBeanBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            RoqDataRecorder recorder) {
        reflectiveClassProducer.produce(
                ReflectiveClassBuildItem.builder(JsonObject.class).serialization().constructors().fields().methods().build());
        for (RoqDataJsonBuildItem roqData : roqDataJsonBuildItems) {
            if (roqData.getData() instanceof JsonObject) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonObject.class)
                        .scope(ApplicationScoped.class)
                        .named(roqData.getName())
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .unremovable()
                        .done());
            } else if (roqData.getData() instanceof JsonArray) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonArray.class)
                        .scope(ApplicationScoped.class)
                        .named(roqData.getName())
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .unremovable()
                        .done());
            }
        }
        for (RoqDataBeanBuildItem beanBuildItem : dataBeanBuildItems) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(beanBuildItem.getBeanClass()).serialization()
                    .constructors().fields().methods().build());
            beansProducer.produce(SyntheticBeanBuildItem.configure(beanBuildItem.getBeanClass())
                    .scope(beanBuildItem.isRecord() ? Singleton.class : ApplicationScoped.class)
                    .named(beanBuildItem.getName())
                    .runtimeValue(recorder.createRoqDataJson(beanBuildItem.getData()))
                    .unremovable()
                    .done());
        }
    }

}