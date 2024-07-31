package io.quarkiverse.roq.data.deployment.converters;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class YamlConverter implements DataConverter {

    private static final YAMLFactory YAML_FACTORY = new YAMLFactory();
    private final YAMLMapper mapper;

    public YamlConverter(YAMLMapper mapper) {
        this.mapper = mapper;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object convert(byte[] content) throws IOException {
        JsonNode rootNode = mapper.readTree(content);
        if (rootNode.isObject()) {
            return new JsonObject(mapper.convertValue(rootNode, Map.class));
        } else if (rootNode.isArray()) {
            return new JsonArray(mapper.convertValue(rootNode, List.class));
        } else {
            throw new IOException("Unsupported YAML root element type: " + rootNode.getNodeType());
        }

    }

    @Override
    public <T> T convertToType(byte[] content, Class<T> clazz) throws IOException {
        final JavaType javaType = mapper.getTypeFactory().constructType(clazz);
        return mapper.readValue(content, javaType);
    }

    @Override
    public <T> List<T> convertToTypedList(byte[] content, Class<T> clazz) throws IOException {
        final CollectionType collectionType = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
        return mapper.readValue(content, collectionType);
    }

}
