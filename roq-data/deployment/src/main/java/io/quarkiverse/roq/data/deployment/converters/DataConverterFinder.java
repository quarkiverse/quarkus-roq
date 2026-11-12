package io.quarkiverse.roq.data.deployment.converters;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.quarkiverse.roq.data.runtime.DataFormat;

public final class DataConverterFinder {

    private final DataConverter jsonConverter;
    private final DataConverter yamlConverter;

    public DataConverterFinder(JsonMapper jsonMapper, YAMLMapper yamlMapper) {
        this.jsonConverter = new JsonConverter(jsonMapper);
        this.yamlConverter = new YamlConverter(yamlMapper);
    }

    public DataConverter fromFileName(String fileName) {
        return DataFormat.fromFileName(fileName)
                .map(format -> switch (format) {
                    case YAML -> yamlConverter;
                    case JSON -> jsonConverter;
                })
                .orElse(null);
    }

}
