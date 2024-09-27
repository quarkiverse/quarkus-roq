package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public record DocumentInfo(
        String collection,
        Integer previousIndex,
        Integer nextIndex) {
}
