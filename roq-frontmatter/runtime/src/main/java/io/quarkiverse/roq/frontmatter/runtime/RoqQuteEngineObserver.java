package io.quarkiverse.roq.frontmatter.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.qute.EngineBuilder;

@ApplicationScoped
public class RoqQuteEngineObserver {

    void configureEngine(@Observes EngineBuilder builder) {
        builder.addParserHook(c -> {
            if (c.getTemplateId().startsWith("layouts")) {
                // Fixes https://github.com/quarkiverse/quarkus-roq/issues/530
                c.addContentFilter(s -> "");
                return;
            }
        });
    }
}
