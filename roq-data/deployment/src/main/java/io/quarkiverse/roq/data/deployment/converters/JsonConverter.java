package io.quarkiverse.roq.data.deployment.converters;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.vertx.core.json.Json;

public class JsonConverter implements DataConverter {
    @Override
    public Object convert(String content) {
        return Json.decodeValue(content);
    }
}
