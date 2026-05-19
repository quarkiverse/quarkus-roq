package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import io.quarkiverse.roq.data.deployment.exception.DataBindingException;
import io.quarkiverse.roq.data.deployment.exception.DataReadingException;
import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataBeanBuildItem;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqDataConverterProcessor {

    @BuildStep
    void convertDataMapping(List<DataMappingBuildItem> mappings,
            BuildProducer<RoqDataBeanBuildItem> beans) {

        for (DataMappingBuildItem mapping : mappings) {
            if (mapping.isParentType()) {
                switch (mapping.getMappingType()) {
                    case OBJECT_DIR -> produceCollectionBean(beans, mapping, true);
                    case ARRAY_DIR, ARRAY_FILE -> produceCollectionBean(beans, mapping, false);
                    case OBJECT_FILE -> throw new DataReadingException(
                            RoqException.builder("Unable to convert data directory")
                                    .detail("""
                                            Could not convert directory %s as a %s.
                                            This is usually an issue in Roq. A mapping with parent should not have types other than OBJECT_DIR or ARRAY_DIR.
                                            """
                                            .formatted(mapping.sourceFile(), mapping.getClassName()))
                                    .hint("Verify the data file structure matches the @DataMapping class fields"));
                }
            } else {
                Class<?> beanClass = loadClass(mapping.getClassName().toString());
                try {
                    final Object data = mapping.getConverter().convertToType(mapping.getContent(), beanClass);
                    beans.produce(new RoqDataBeanBuildItem(mapping.getName(), beanClass, data, mapping.isRecord()));
                } catch (IOException e) {
                    throw new DataReadingException(
                            RoqException.builder("Unable to convert data file")
                                    .detail("Could not convert file %s as a %s"
                                            .formatted(mapping.sourceFile(), mapping.getClassName()))
                                    .sourceFilePath(mapping.sourceFile().toString())
                                    .hint("Verify the data file structure matches the @DataMapping class fields")
                                    .cause(e));
                }
            }
        }

    }

    private void produceCollectionBean(BuildProducer<RoqDataBeanBuildItem> beans, DataMappingBuildItem mapping,
            boolean isMap) {
        Class<?> parentClass = loadClass(mapping.getParentType().toString());
        Class<?> collectionClass = isMap ? Map.class : List.class;

        final Constructor<?> constructor;
        try {
            constructor = parentClass.getConstructor(collectionClass);
        } catch (NoSuchMethodException e) {
            String collectionStr = isMap ? "Map<String, %s>" : "List<%s>";
            throw new DataBindingException(
                    RoqException.builder("@DataMapping %s binding error".formatted(isMap ? "map" : "list"))
                            .detail(("Class %s should declare a constructor with a " + collectionStr + " as unique parameter")
                                    .formatted(parentClass.getName(), mapping.getClassName()))
                            .sourceFilePath(mapping.sourceFile().toString())
                            .hint(("Add a public constructor like: public %s(" + collectionStr + " items)")
                                    .formatted(parentClass.getSimpleName(), mapping.getClassName()))
                            .cause(e));
        }

        try {
            final Class<?> itemClass = loadClass(mapping.getClassName().toString());
            final Object collection;
            if (isMap) {
                collection = mapping.getConverter().convertToTypedMap(mapping.getContent(), itemClass);
            } else {
                collection = mapping.getConverter().convertToTypedList(mapping.getContent(), itemClass);
            }
            final Object data = constructor.newInstance(collection);
            beans.produce(new RoqDataBeanBuildItem(mapping.getName(), parentClass, data, mapping.isRecord()));
        } catch (IOException e) {
            String collectionStr = isMap ? "Map<String,%s>" : "List<%s>";
            throw new DataReadingException(
                    RoqException.builder("Unable to read data file")
                            .detail(("Could not read file %s as a " + collectionStr)
                                    .formatted(mapping.sourceFile(), mapping.getClassName()))
                            .sourceFilePath(mapping.sourceFile().toString())
                            .hint("Check that the data file format matches the expected %s structure"
                                    .formatted(isMap ? "map" : "list"))
                            .cause(e));
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class %s not found".formatted(className), e);
        }
    }
}
