package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import io.quarkiverse.roq.data.deployment.exception.DataListBindingException;
import io.quarkiverse.roq.data.deployment.exception.DataReadingException;
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
                    throw new IllegalStateException("Class %s not found".formatted(mapping.getParentType().toString()), e);
                }

                final Constructor<?> constructor;
                try {
                    constructor = parentClass.getConstructor(List.class);
                } catch (NoSuchMethodException e) {
                    throw new DataListBindingException(
                            "@DataMapping for list in %s should declare a constructor with a List<%s> as unique parameter"
                                    .formatted(parentClass.getName(), mapping.getClassName()),
                            e);
                }

                try {
                    final Class<?> itemClass = Class.forName(mapping.getClassName().toString(), false,
                            Thread.currentThread().getContextClassLoader());
                    final List<?> list = mapping.getConverter().convertToTypedList(mapping.getContent(), itemClass);
                    final Object data = constructor.newInstance(list);
                    beans.produce(new RoqDataBeanBuildItem(mapping.getName(), parentClass, data, mapping.isRecord()));
                } catch (IOException e) {
                    throw new DataReadingException("Unable to read data in file %s as a List<%s>"
                            .formatted(mapping.sourceFile(), mapping.getClassName()), e);
                } catch (ClassNotFoundException | InvocationTargetException | InstantiationException
                        | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Class<?> beanClass;
                try {
                    beanClass = Class.forName(mapping.getClassName().toString(), false,
                            Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Class %s not found".formatted(mapping.getClassName().toString()), e);
                }
                try {
                    final Object data = mapping.getConverter().convertToType(mapping.getContent(), beanClass);
                    beans.produce(new RoqDataBeanBuildItem(mapping.getName(), beanClass, data, mapping.isRecord()));
                } catch (IOException e) {
                    throw new DataReadingException(
                            "Unable to convert data in file %s as a %s".formatted(mapping.sourceFile(), mapping.getClassName()),
                            e);
                }
            }
        }

    }
}