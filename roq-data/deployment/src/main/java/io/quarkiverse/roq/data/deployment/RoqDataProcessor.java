package io.quarkiverse.roq.data.deployment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.items.ParentMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataMappingBuildItem;
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

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateInjectable(BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            RoqDataRecorder recorder) {

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

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateDataMappingFromParentTypeMapping(List<ParentMappingBuildItem> mappings,
            BuildProducer<SyntheticBeanBuildItem> beans,
            RoqDataRecorder recorder) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {

        LOGGER.infof("Handling parent mapping items! %d", mappings.size());

        for (ParentMappingBuildItem mapping : mappings) {

            final Class<?> aClass = Thread.currentThread().getContextClassLoader()
                    .loadClass(mapping.getParentType().toString());

            final Constructor<?> ctor = aClass.getConstructor(List.class);

            if (!(mapping.getJson() instanceof final JsonArray jsonArray)) {
                continue;
            }
            final List<Object> list = new ArrayList<>();

            for (Object item : jsonArray) {
                final JsonObject jsonObject = (JsonObject) item;
                try {
                    final Object o = jsonObject.mapTo(Class.forName(mapping.getListType().toString()));
                    list.add(o);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

            final Object parentObject = ctor.newInstance(list);

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
    void generateDataMappings(RoqDataRecorder roqDataRecorder, List<RoqDataMappingBuildItem> roqDataMappings,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeansProducer) throws ClassNotFoundException {
        for (RoqDataMappingBuildItem roqDataMapping : roqDataMappings) {

            Class<?> clazz = Class.forName(roqDataMapping.getClassName().toString());
            if (roqDataMapping.getData() instanceof JsonObject jsonObject) {
                Object object = jsonObject.mapTo(clazz);
                syntheticBeansProducer.produce(SyntheticBeanBuildItem.configure(clazz)
                        .scope(roqDataMapping.isRecord() ? Singleton.class : ApplicationScoped.class)
                        .addQualifier().annotation(Named.class).addValue("value", roqDataMapping.getName()).done()
                        .setRuntimeInit()
                        .runtimeValue(roqDataRecorder.createRoqDataJson(object))
                        .done());
            } else if (roqDataMapping.getData() instanceof JsonArray array) {

                if (roqDataMapping.isRecord()) {
                    LOGGER.infof("Error with roqDataMapping: %s", roqDataMapping);
                    throw new IllegalStateException(
                            "You are trying to map the file %s(.json|.yaml|.yml) to the class %s, but you cannot map an array to multiple beans of a record type. You need to use an old POJO class instead."
                                    .formatted(
                                            roqDataMapping.getName(), roqDataMapping.getClassName()));
                }

                AtomicInteger index = new AtomicInteger(0);
                array.stream().forEach(item -> {
                    JsonObject jsonObject = JsonObject.mapFrom(item);
                    Object object = jsonObject.mapTo(clazz);
                    syntheticBeansProducer
                            .produce(SyntheticBeanBuildItem.configure(clazz)
                                    .scope(Singleton.class)
                                    .addQualifier().annotation(Named.class).addValue("value", "%s%d".formatted(
                                            roqDataMapping.getName(), index.getAndIncrement()))
                                    .done()
                                    .setRuntimeInit()
                                    .runtimeValue(roqDataRecorder.createRoqDataJson(object))
                                    .done());
                });
            }
        }
    }

}
