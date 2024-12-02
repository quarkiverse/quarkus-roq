package io.quarkiverse.roq.plugin.series.runtime;

import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;

public record Series(List<Serie> series) {

    public record Serie(String title, List<Entry> entries) {

        public record Entry(String title) {
            public String link() {
                return "/posts/%s".formatted(RoqTemplateExtension.slugify(title));
            }
        }

    }

    public Serie findSerie(String title) {
        return series.stream()
                .filter(serie -> serie.title().equals(title))
                .findFirst()
                .orElse(null);
    }
}
