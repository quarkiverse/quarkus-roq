package io.quarkiverse.roq.deployment;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.deployment.config.RoqJacksonConfig;
import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkus.arc.impl.Reflections;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqJacksonProcessor {

    private static final Logger LOG = Logger.getLogger(RoqJacksonProcessor.class);

    @BuildStep
    RoqJacksonBuildItem findProject(RoqJacksonConfig jacksonConfig) {
        YAMLMapper.Builder yamlMapperBuilder = YAMLMapper.builder();
        JsonMapper.Builder jsonMapperBuilder = JsonMapper.builder();
        if (!jacksonConfig.failOnUnknownProperties()) {
            // this feature is enabled by default, so we disable it
            yamlMapperBuilder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            jsonMapperBuilder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
        if (!jacksonConfig.failOnEmptyBeans()) {
            // this feature is enabled by default, so we disable it
            yamlMapperBuilder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            jsonMapperBuilder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        }

        if (jacksonConfig.acceptCaseInsensitiveEnums()) {
            yamlMapperBuilder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            jsonMapperBuilder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        }
        final Optional<PropertyNamingStrategy> propertyNamingStrategy = determinePropertyNamingStrategyClassName(jacksonConfig);
        if (propertyNamingStrategy.isPresent()) {
            jsonMapperBuilder.propertyNamingStrategy(propertyNamingStrategy.get());
            yamlMapperBuilder.propertyNamingStrategy(propertyNamingStrategy.get());
        }
        return new RoqJacksonBuildItem(jsonMapperBuilder.build(), yamlMapperBuilder.build());
    }

    private Optional<PropertyNamingStrategy> determinePropertyNamingStrategyClassName(RoqJacksonConfig jacksonConfig) {
        if (jacksonConfig.propertyNamingStrategy().isEmpty()) {
            return Optional.empty();
        }
        var propertyNamingStrategy = jacksonConfig.propertyNamingStrategy().get();
        Field field;

        try {
            // let's first try and see if the value is a constant defined in PropertyNamingStrategies
            field = Reflections.findField(PropertyNamingStrategies.class, propertyNamingStrategy);
        } catch (Exception e) {
            // the provided value does not correspond to any of the defined constants, so let's see if it's actually a class name
            try {
                var clazz = Thread.currentThread().getContextClassLoader().loadClass(propertyNamingStrategy);
                if (PropertyNamingStrategy.class.isAssignableFrom(clazz)) {
                    return Optional.of((PropertyNamingStrategy) clazz.getConstructor().newInstance());
                }
                throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                    | InvocationTargetException ex) {
                throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
            }
        }

        try {
            // we have a matching field, so let's see if the type is correct
            final Object value = field.get(null);
            Class<?> clazz = value.getClass();
            if (PropertyNamingStrategy.class.isAssignableFrom(clazz)) {
                return Optional.of((PropertyNamingStrategy) value);
            }
            throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
        } catch (IllegalAccessException e) {
            // shouldn't ever happen
            throw new RuntimeException(invalidPropertyNameStrategyValueMessage(propertyNamingStrategy));
        }
    }

    private static String invalidPropertyNameStrategyValueMessage(String propertyNamingStrategy) {
        return "Unable to determine the property naming strategy for value '" + propertyNamingStrategy
                + "'. Make sure that the value is either a fully qualified class name of a subclass of '"
                + PropertyNamingStrategy.class.getName()
                + "' or one of the constants defined in '" + PropertyNamingStrategies.class.getName() + "'.";
    }
}
