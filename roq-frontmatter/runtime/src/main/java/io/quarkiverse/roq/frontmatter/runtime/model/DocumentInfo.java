package io.quarkiverse.roq.frontmatter.runtime.model;

public record DocumentInfo(
        String collection,
        Integer previousIndex,
        Integer nextIndex) {
}
