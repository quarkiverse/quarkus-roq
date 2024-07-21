package io.quarkiverse.roq.data.deployment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.items.NormalDataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.ParentDataMappingBuildItem;
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

class RoqDataProcessor {

    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqDataProcessor.class);
    private static final String FEATURE = "roq-data";
    private static final String ANNOTATION_VALUE = "value";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateSyntheticBeans(BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            RoqDataRecorder recorder) {

        for (RoqDataJsonBuildItem roqData : roqDataJsonBuildItems) {
            if (roqData.getData() instanceof JsonObject) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonObject.class)
                        .scope(ApplicationScoped.class)
                        .setRuntimeInit()
                        .addQualifier().annotation(Named.class).addValue(ANNOTATION_VALUE, roqData.getName()).done()
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .done());
            } else if (roqData.getData() instanceof JsonArray) {
                beansProducer.produce(SyntheticBeanBuildItem.configure(JsonArray.class)
                        .scope(ApplicationScoped.class)
                        .setRuntimeInit()
                        .addQualifier().annotation(Named.class).addValue(ANNOTATION_VALUE, roqData.getName()).done()
                        .runtimeValue(recorder.createRoqDataJson(roqData.getData()))
                        .done());
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateSyntheticBeanFromParentTypeMapping(List<ParentDataMappingBuildItem> mappings,
            BuildProducer<SyntheticBeanBuildItem> beans,
            RoqDataRecorder recorder) throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException,
            InstantiationException, IllegalAccessException {

        for (ParentDataMappingBuildItem mapping : mappings) {

            final Class<?> aClass = Thread.currentThread().getContextClassLoader()
                    .loadClass(mapping.getParentType().toString());

            final Constructor<?> constructor = aClass.getConstructor(List.class);

            if (!(mapping.getJson() instanceof final JsonArray jsonArray)) {
                continue;
            }
            final List<Object> list = new ArrayList<>();

            for (Object item : jsonArray) {
                final JsonObject jsonObject = (JsonObject) item;
                try {
                    final Object o = jsonObject.mapTo(Class.forName(mapping.getListType().toString()));
                    list.add(o);
                } catch (Exception e) {
                    LOGGER.error("Unable to convert an item in the array to %s.".formatted(mapping.getListType()), e);
                    throw e;
                }
            }

            final Object parentObject = constructor.newInstance(list);

            beans.produce(
                    SyntheticBeanBuildItem.configure(mapping.getParentType())
                            .scope(Singleton.class)
                            .setRuntimeInit()
                            .runtimeValue(recorder.createRoqDataJson(parentObject))
                            .done()

            );
        }

    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateDataMappings(RoqDataRecorder roqDataRecorder, List<NormalDataMappingBuildItem> normalDataMappingBuildItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeansProducer) throws ClassNotFoundException {
        for (NormalDataMappingBuildItem normalDataMapping : normalDataMappingBuildItems) {

            Class<?> clazz = Class.forName(normalDataMapping.getClassName().toString());
            if (normalDataMapping.getData() instanceof JsonObject jsonObject) {
                Object object = jsonObject.mapTo(clazz);
                syntheticBeansProducer.produce(SyntheticBeanBuildItem.configure(clazz)
                        .scope(normalDataMapping.isRecord() ? Singleton.class : ApplicationScoped.class)
                        .addQualifier().annotation(Named.class).addValue(ANNOTATION_VALUE, normalDataMapping.getName()).done()
                        .setRuntimeInit()
                        .runtimeValue(roqDataRecorder.createRoqDataJson(object))
                        .done());
            }
        }
    }

}
