package io.quarkiverse.roq.deployment.items;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqJacksonBuildItem extends SimpleBuildItem {

    private final JsonMapper jsonMapper;
    private final YAMLMapper yamlMapper;

    public RoqJacksonBuildItem(JsonMapper jsonMapper, YAMLMapper yamlMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = yamlMapper;
    }

    public JsonMapper getJsonMapper() {
        return jsonMapper;
    }

    public YAMLMapper getYamlMapper() {
        return yamlMapper;
    }
}
