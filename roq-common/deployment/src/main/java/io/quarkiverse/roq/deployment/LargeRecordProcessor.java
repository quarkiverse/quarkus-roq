package io.quarkiverse.roq.deployment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkiverse.roq.deployment.items.LargeRecordBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class LargeRecordProcessor {

    @BuildStep
    void processRecordableResources(
            List<LargeRecordBuildItem> items,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        if (items.isEmpty()) {
            return;
        }
        Set<Class<?>> allClasses = new HashSet<>();
        for (LargeRecordBuildItem item : items) {
            generatedResourceProducer.produce(new GeneratedResourceBuildItem(item.resourcePath(), item.data()));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(item.resourcePath()));
            allClasses.addAll(item.serializableClasses());
        }
        if (!allClasses.isEmpty()) {
            reflectiveClassProducer.produce(
                    ReflectiveClassBuildItem.builder(allClasses.toArray(Class[]::new)).serialization().build());
        }
    }
}
