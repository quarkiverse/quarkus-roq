package io.quarkiverse.roq.generator.runtime;

import java.util.Comparator;
import java.util.List;

public record StaticPages(List<StaticPage> pages) {

    public static List<StaticPage> merge(List<StaticPages> staticPages) {
        return staticPages.stream().map(StaticPages::pages).flatMap(List::stream)
                .sorted(Comparator.comparing(StaticPage::outputPath)).toList();
    }
}
