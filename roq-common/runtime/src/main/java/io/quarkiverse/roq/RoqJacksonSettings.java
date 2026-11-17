package io.quarkiverse.roq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The Jackson settings Roq reads data files with ({@code quarkus.roq.jackson.*}), shared by everything that maps data
 * files, so that data is written the same way it is read. It can be recorded, to configure mappers at runtime.
 *
 * @param failOnUnknownProperties fail on a property the type doesn't declare
 * @param failOnEmptyBeans fail on a type without accessors
 * @param acceptCaseInsensitiveEnums ignore case when reading enums
 * @param propertyNamingStrategy the class name of the property naming strategy, or null for the Jackson default
 */
public record RoqJacksonSettings(boolean failOnUnknownProperties, boolean failOnEmptyBeans,
        boolean acceptCaseInsensitiveEnums, String propertyNamingStrategy) {

    /** The Jackson defaults, as used when nothing is configured. */
    public static final RoqJacksonSettings DEFAULTS = new RoqJacksonSettings(true, true, false, null);

    public RoqJacksonSettings withPropertyNamingStrategy(PropertyNamingStrategy strategy) {
        return new RoqJacksonSettings(failOnUnknownProperties, failOnEmptyBeans, acceptCaseInsensitiveEnums,
                strategy == null ? null : strategy.getClass().getName());
    }

    /**
     * Apply the settings to a mapper builder. java.time types are read from and written as ISO-8601 text.
     */
    public <B extends MapperBuilder<?, B>> B configure(B builder) {
        builder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, failOnEmptyBeans)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, acceptCaseInsensitiveEnums)
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PropertyNamingStrategy strategy = namingStrategy();
        if (strategy != null) {
            builder.propertyNamingStrategy(strategy);
        }
        return builder;
    }

    /** A new instance of the naming strategy, or null when none is set. */
    public PropertyNamingStrategy namingStrategy() {
        if (propertyNamingStrategy == null) {
            return null;
        }
        try {
            return (PropertyNamingStrategy) Class
                    .forName(propertyNamingStrategy, true, Thread.currentThread().getContextClassLoader())
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to instantiate property naming strategy " + propertyNamingStrategy, e);
        }
    }
}
