package io.quarkiverse.roq.generator.runtime;

import java.util.Comparator;
import java.util.List;

public record RoqSelection(List<SelectedPath> paths) {

    public static List<SelectedPath> merge(List<RoqSelection> staticPaths) {
        return staticPaths.stream().map(RoqSelection::paths).flatMap(List::stream)
                .sorted(Comparator.comparing(SelectedPath::outputPath)).toList();
    }
}
