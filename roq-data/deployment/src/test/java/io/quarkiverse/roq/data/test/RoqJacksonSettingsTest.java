package io.quarkiverse.roq.data.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.RoqJacksonSettings;
import io.quarkiverse.roq.data.runtime.DataFormat;
import io.quarkiverse.roq.data.runtime.DataSerializer;

/** The writer maps data with the same settings the data is read with. */
class RoqJacksonSettingsTest {

    record Release(String latestPatch, LocalDate releaseDate) {
    }

    @Test
    void writerUsesTheReaderSettings() throws Exception {
        RoqJacksonSettings settings = RoqJacksonSettings.DEFAULTS
                .withPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        String yaml = """
                latest_patch: 3.20.6
                release_date: 2025-03-26
                """;
        Release read = settings.configure(YAMLMapper.builder()).build().readValue(yaml, Release.class);
        assertThat(read).isEqualTo(new Release("3.20.6", LocalDate.of(2025, 3, 26)));
        assertThat(new DataSerializer(settings).write(read, yaml, DataFormat.YAML)).isSameAs(yaml);
        assertThat(new DataSerializer(settings).write(new Release("3.20.7", read.releaseDate()), yaml, DataFormat.YAML))
                .isEqualTo(yaml.replace("3.20.6", "3.20.7"));
    }

    @Test
    void settingsCanBeRecorded() {
        // a record of strings and booleans, which the bytecode recorder handles; the strategy is rebuilt from its name
        RoqJacksonSettings settings = RoqJacksonSettings.DEFAULTS
                .withPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
        assertThat(settings.namingStrategy()).isInstanceOf(PropertyNamingStrategies.KebabCaseStrategy.class);
        assertThat(RoqJacksonSettings.DEFAULTS.namingStrategy()).isNull();
    }
}
