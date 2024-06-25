package io.quarkiverse.roq.data.deployment;

import java.util.function.Function;

import io.vertx.core.json.JsonObject;

public class JsonObjectConverter {

    private static final YamlConverter YAML_CONVERTER = new YamlConverter();
    private static final JsonConverter JSON_CONVERTER = new JsonConverter();

    public enum Extensions {
        JSON(".json", JSON_CONVERTER),
        YAML(".yaml", YAML_CONVERTER),
        YML(".yml", YAML_CONVERTER);

        private final String extension;
        private final Function<String, JsonObject> converter;

        Extensions(String extension, Function<String, JsonObject> converter) {
            this.extension = extension;
            this.converter = converter;
        }

        public String getExtension() {
            return extension;
        }

        public JsonObject convert(String content) {
            return this.converter.apply(content);
        }
    }

    public static Extensions findExtensionConverter(String filename) {
        for (Extensions extension : Extensions.values()) {
            if (filename.endsWith(extension.extension)) {
                return extension;
            }
        }
        return null;
    }
}
