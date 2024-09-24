package io.quarkiverse.roq.data.deployment;

import java.lang.reflect.Constructor;
import java.util.List;

import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqDataConverterProcessor {

    @BuildStep
    void convertDataMapping(List<DataMappingBuildItem> mappings,
            BuildProducer<RoqDataBeanBuildItem> beans) {

        for (DataMappingBuildItem mapping : mappings) {
            if (mapping.isParentType()) {
                Class<?> parentClass;
                try {
                    parentClass = Class.forName(mapping.getParentType().toString(), false,
                            Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Class %s not found".formatted(mapping.getParentType().toString()), e);
                }

                final Constructor<?> constructor;
                try {
                    constructor = parentClass.getConstructor(List.class);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(
                            "@DataMapping for list should declare a constructor with a List<T> as unique parameter.", e);
                }

                try {
                    final Class<?> itemClass = Class.forName(mapping.getClassName().toString(), false,
                            Thread.currentThread().getContextClassLoader());
                    final List<?> list = mapping.getConverter().convertToTypedList(mapping.getContent(), itemClass);
                    final Object data = constructor.newInstance(list);
                    beans.produce(new RoqDataBeanBuildItem(mapping.getName(), parentClass, data, mapping.isRecord()));
                } catch (Exception e) {
                    throw new RuntimeException("Unable to convert data to a List<%s>.".formatted(mapping.getClassName()), e);
                }
            } else {
                Class<?> beanClass;
                try {
                    beanClass = Class.forName(mapping.getClassName().toString(), false,
                            Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Class %s not found".formatted(mapping.getClassName().toString()), e);
                }
                try {
                    final Object data = mapping.getConverter().convertToType(mapping.getContent(), beanClass);
                    beans.produce(new RoqDataBeanBuildItem(mapping.getName(), beanClass, data, mapping.isRecord()));
                } catch (Exception e) {
                    throw new RuntimeException("Unable to convert data to a %s.".formatted(mapping.getClassName()), e);
                }
            }
        }

    }
}