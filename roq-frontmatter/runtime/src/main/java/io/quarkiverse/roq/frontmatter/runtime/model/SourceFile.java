package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.tools.stringpaths.StringPaths;
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
        return StringPaths.join(siteDirPath, relativePath);
    }

}
