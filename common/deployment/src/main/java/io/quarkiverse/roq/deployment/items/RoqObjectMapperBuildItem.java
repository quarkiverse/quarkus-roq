package io.quarkiverse.roq.deployment.items;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqObjectMapperBuildItem extends SimpleBuildItem {

    private final ObjectMapper objectMapper;

    public RoqObjectMapperBuildItem(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
