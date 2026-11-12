package io.quarkiverse.roq.deployment.items;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.RoqJacksonSettings;
import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqJacksonBuildItem extends SimpleBuildItem {

    private final JsonMapper jsonMapper;
    private final YAMLMapper yamlMapper;
    private final RoqJacksonSettings settings;

    public RoqJacksonBuildItem(JsonMapper jsonMapper, YAMLMapper yamlMapper, RoqJacksonSettings settings) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = yamlMapper;
        this.settings = settings;
    }

    /** The settings both mappers were built with, to build matching mappers at runtime. */
    public RoqJacksonSettings getSettings() {
        return settings;
    }

    public JsonMapper getJsonMapper() {
        return jsonMapper;
    }

    public YAMLMapper getYamlMapper() {
        return yamlMapper;
    }
}
