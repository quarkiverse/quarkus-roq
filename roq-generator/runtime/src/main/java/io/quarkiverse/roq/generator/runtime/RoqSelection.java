package io.quarkiverse.roq.generator.runtime;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record RoqSelection(List<SelectedPath> paths) {

    public static List<SelectedPath> prepare(RoqGeneratorConfig config, List<RoqSelection> selection) {
        Set<String> seenPaths = new HashSet<>();
        return selection.stream().map(RoqSelection::paths).flatMap(List::stream)
                .filter(selectedPath -> seenPaths.add(selectedPath.path()))
                .map(s -> s.clean(config.pathReplace()))
                .sorted(Comparator.comparing(SelectedPath::outputPath)).toList();
    }

}
