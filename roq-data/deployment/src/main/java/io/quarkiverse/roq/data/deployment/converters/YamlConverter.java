package io.quarkiverse.roq.data.deployment.converters;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;

public class YamlConverter implements DataConverter {

    private static final YAMLFactory YAML_FACTORY = new YAMLFactory();
    private final ObjectMapper objectMapper;

    public YamlConverter() {
        this.objectMapper = DatabindCodec.mapper().copyWith(YAML_FACTORY);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object convert(byte[] content) throws IOException {
        JsonNode rootNode = objectMapper.readTree(content);
        if (rootNode.isObject()) {
            return new JsonObject(objectMapper.convertValue(rootNode, Map.class));
        } else if (rootNode.isArray()) {
            return new JsonArray(objectMapper.convertValue(rootNode, List.class));
        } else {
            throw new IOException("Unsupported YAML root element type: " + rootNode.getNodeType());
        }

    }

    @Override
    public <T> T convertToType(byte[] content, Class<T> clazz) throws IOException {
        final JavaType javaType = objectMapper.getTypeFactory().constructType(clazz);
        return objectMapper.readValue(content, javaType);
    }

    @Override
    public <T> List<T> convertToTypedList(byte[] content, Class<T> clazz) throws IOException {
        final CollectionType collectionType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        return objectMapper.readValue(content, collectionType);
    }

}
