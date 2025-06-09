package io.quarkiverse.roq.plugin.diagram.runtime.client;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import io.quarkus.jackson.ObjectMapperCustomizer;

@Singleton
public class ObjectMapperConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    }
}
