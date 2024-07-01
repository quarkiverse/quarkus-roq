package io.quarkiverse.roq.data.deployment;

public class JsonObjectConverter {

    private static final YamlConverter YAML_CONVERTER = new YamlConverter();
    private static final JsonConverter JSON_CONVERTER = new JsonConverter();

    public enum Extensions {
        JSON(".json", JSON_CONVERTER),
        YAML(".yaml", YAML_CONVERTER),
        YML(".yml", YAML_CONVERTER);

        private final String extension;
        private final DataConverter converter;

        Extensions(String extension, DataConverter converter) {
            this.extension = extension;
            this.converter = converter;
        }

        public String getExtension() {
            return extension;
        }

        public Object convert(String content) {
            return this.converter.convert(content);
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
