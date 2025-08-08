package io.quarkiverse.roq.frontmatter.runtime.model;

import java.nio.file.Paths;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

/**
 * Represents a source file in a Roq site.
 *
 * @param siteDir The root directory of the site project.
 * @param relativePath The path to the file relative to {@code siteDir}. Includes folders like {@code content/} or
 *        {@code templates/}.
 */
@TemplateData
@Vetoed
public record SourceFile(String siteDirPath, String relativePath) {

    public String absolutePath() {
        return Paths.get(siteDirPath, relativePath).toString();
    }

}
