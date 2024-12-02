package io.quarkiverse.roq.plugin.series.runtime;

import java.util.List;
import java.util.Map;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class SeriesRecorder {
    public RuntimeValue<Series> generateSeries(Map<String, List<String>> serieTemplates) {
        return new RuntimeValue<>(
                new Series(serieTemplates.entrySet().stream()
                        .map(entry -> new Series.Serie(entry.getKey(),
                                entry.getValue().stream().map(Series.Serie.Entry::new).toList()))
                        .toList()));
    }
}
