package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class YamlConverter implements DataConverter {

    private static final ObjectMapper YAML_READER = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    @Override
    public Object convert(String content) {
        try {
            JsonNode rootNode = YAML_READER.readTree(content);
            if (rootNode.isObject()) {
                return new JsonObject(YAML_READER.convertValue(rootNode, Map.class));
            } else if (rootNode.isArray()) {
                return new JsonArray(YAML_READER.convertValue(rootNode, List.class));
            } else {
                throw new IOException("Unsupported YAML root element type: " + rootNode.getNodeType());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
