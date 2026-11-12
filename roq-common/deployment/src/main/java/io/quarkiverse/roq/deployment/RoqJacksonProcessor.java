package io.quarkiverse.roq.deployment;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.RoqJacksonSettings;
import io.quarkiverse.roq.deployment.config.RoqJacksonConfig;
import io.quarkiverse.roq.deployment.exception.RoqJacksonConfigException;
import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkus.arc.impl.Reflections;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqJacksonProcessor {

    @BuildStep
    RoqJacksonBuildItem findProject(RoqJacksonConfig jacksonConfig) {
        final RoqJacksonSettings settings = new RoqJacksonSettings(jacksonConfig.failOnUnknownProperties(),
                jacksonConfig.failOnEmptyBeans(), jacksonConfig.acceptCaseInsensitiveEnums(), null)
                .withPropertyNamingStrategy(determinePropertyNamingStrategyClassName(jacksonConfig).orElse(null));
        return new RoqJacksonBuildItem(settings.configure(JsonMapper.builder()).build(),
                settings.configure(YAMLMapper.builder()).build(), settings);
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
            try {
                var clazz = Thread.currentThread().getContextClassLoader().loadClass(propertyNamingStrategy);
                if (PropertyNamingStrategy.class.isAssignableFrom(clazz)) {
                    return Optional.of((PropertyNamingStrategy) clazz.getConstructor().newInstance());
                }
                throw invalidPropertyNamingStrategyException(propertyNamingStrategy, null);
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                    | InvocationTargetException ex) {
                throw invalidPropertyNamingStrategyException(propertyNamingStrategy, ex);
            }
        }

        try {
            final Object value = field.get(null);
            Class<?> clazz = value.getClass();
            if (PropertyNamingStrategy.class.isAssignableFrom(clazz)) {
                return Optional.of((PropertyNamingStrategy) value);
            }
            throw invalidPropertyNamingStrategyException(propertyNamingStrategy, null);
        } catch (IllegalAccessException e) {
            throw invalidPropertyNamingStrategyException(propertyNamingStrategy, e);
        }
    }

    private static RoqJacksonConfigException invalidPropertyNamingStrategyException(String propertyNamingStrategy,
            Throwable cause) {
        RoqException.Builder builder = RoqException.builder("Invalid Jackson property naming strategy")
                .detail("Unable to determine the property naming strategy for value '%s'".formatted(propertyNamingStrategy))
                .hint("Use a fully qualified class name extending %s or a constant from %s"
                        .formatted(PropertyNamingStrategy.class.getName(),
                                PropertyNamingStrategies.class.getName()));
        if (cause != null) {
            builder.cause(cause);
        }
        return new RoqJacksonConfigException(builder);
    }
}