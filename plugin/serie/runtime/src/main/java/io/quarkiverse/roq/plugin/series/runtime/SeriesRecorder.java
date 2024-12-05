package io.quarkiverse.roq.plugin.series.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class SeriesRecorder {
    public RuntimeValue<Series> generateSeries(Map<String, List<String>> series) {
        return new RuntimeValue<>(
                new Series(series.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> new Series.Serie(entry.getKey(), entry.getValue())))));
    }

}
