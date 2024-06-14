package io.quarkiverse.statiq.runtime;

import java.util.Comparator;
import java.util.List;

public record StatiqPages(List<StatiqPage> pages) {

    public static List<StatiqPage> merge(List<StatiqPages> statiqPages) {
        return statiqPages.stream().map(StatiqPages::pages).flatMap(List::stream)
                .sorted(Comparator.comparing(StatiqPage::outputPath)).toList();
    }
}
