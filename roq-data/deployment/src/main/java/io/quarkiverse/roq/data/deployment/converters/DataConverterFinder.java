package io.quarkiverse.roq.data.deployment.converters;

import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.data.deployment.DataConverter;

public final class DataConverterFinder {

    private final Map<String, DataConverter> converterByExtension;

    public DataConverterFinder(JsonMapper jsonMapper, YAMLMapper yamlMapper) {
        DataConverter jsonConverter = new JsonConverter(jsonMapper);
        DataConverter yamlConverter = new YamlConverter(yamlMapper);
        this.converterByExtension = Map.of(
                "yaml", yamlConverter,
                "yml", yamlConverter,
                "json", jsonConverter);
    }

    public DataConverter fromFileName(String fileName) {
        if (!fileName.contains(".")) {
            return null;
        }
        final String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
        return converterByExtension.get(extension);
    }

}
