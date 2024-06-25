package io.quarkiverse.roq.data.deployment;

import java.util.Map;
import java.util.function.Function;

import org.yaml.snakeyaml.Yaml;

import io.vertx.core.json.JsonObject;

public class YamlConverter implements Function<String, JsonObject> {
    @Override
    public JsonObject apply(String content) {
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.loadAs(content, Map.class);
        return new JsonObject(map);
    }
}
