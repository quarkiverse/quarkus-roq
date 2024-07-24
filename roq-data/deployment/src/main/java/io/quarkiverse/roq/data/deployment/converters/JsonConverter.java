package io.quarkiverse.roq.data.deployment.converters;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;

public class JsonConverter implements DataConverter {
    @Override
    public Object convert(byte[] content) {
        return Json.decodeValue(Buffer.buffer(content));
    }

    @Override
    public <T> T convertToType(byte[] content, Class<T> clazz) throws IOException {
        final ObjectMapper mapper = DatabindCodec.mapper();
        final JavaType javaType = mapper.getTypeFactory().constructType(clazz);
        return mapper.readValue(content, javaType);
    }

    @Override
    public <T> List<T> convertToTypedList(byte[] content, Class<T> clazz) throws IOException {
        final ObjectMapper mapper = DatabindCodec.mapper();
        final CollectionType collectionType = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
        return mapper.readValue(content, collectionType);
    }
}
